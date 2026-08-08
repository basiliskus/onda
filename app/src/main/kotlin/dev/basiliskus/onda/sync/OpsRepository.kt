package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.model.PlaylistsState
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.ops.LegacyPendingMigration
import dev.basiliskus.onda.core.ops.Op
import dev.basiliskus.onda.core.ops.OpStation
import dev.basiliskus.onda.core.ops.OpsJournal
import dev.basiliskus.onda.core.ops.applyOps
import dev.basiliskus.onda.core.radiobrowser.RbStation
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns ops.jsonl — the append-only journal of playlist edits the desktop side consumes.
 * The in-memory mirror is always compacted; the on-disk journal is rewritten (via the
 * crash-safe tmp/bak choreography in PlaylistRepository) after every mutation.
 */
class OpsRepository(
    private val playlists: PlaylistsSource,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _ops = MutableStateFlow<List<Op>>(emptyList())
    /** Urls with an unconsumed quick-star add (playlist == null) — drives the Discover
     *  star. Targeted adds neither light the star nor respond to it; they surface as
     *  pending rows in the playlist UI instead. */
    val starredUrls: StateFlow<Set<String>> = _ops
        .map { ops ->
            ops.filterIsInstance<Op.Add>().filter { it.playlist == null }.map { it.station.url }.toSet()
        }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())
    /**
     * THE derived flow — raw playlists with the compacted journal overlaid. Every
     * playlist-content consumer (playlist UI, media session station index, Auto browse
     * tree, playback resumption) renders from this; raw PlaylistRepository.state remains
     * only for NeedsFolder/Loading gating and the scanning flag. NeedsFolder/Loading pass
     * through untouched, so gating semantics are unchanged for consumers of this flow too.
     */
    val effectivePlaylists: StateFlow<PlaylistsState> =
        combine(playlists.state, _ops) { st, opList ->
            if (st is PlaylistsState.Ready) PlaylistsState.Ready(applyOps(st.playlists, opList)) else st
        }.stateIn(scope, SharingStarted.Eagerly, playlists.state.value)
    /** Buffered channel, not a replay-less SharedFlow: an error emitted while no collector
     *  exists (Activity recreating, app backgrounded) waits for the next one instead of
     *  vanishing — a dropped write-failure was the one report that mattered (GitHub #30).
     *  Single consumer by design: the snackbar host.
     *  enforced by: UndoIntegrityTest."an ops error emitted with no collector reaches a late collector" */
    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors: Flow<String> = _errors.receiveAsFlow()
    private val mutex = Mutex()
    /** Bumped (under [mutex]) on every _ops change; guards [UndoToken] validity. */
    private var generation = 0L

    /**
     * Opaque handle returned by [delete] and [moveToPlaylist] for [undo]. Compaction can
     * consume the emitted ops themselves (deleting a pending add, or moving a just-added
     * row), so undo is snapshot-based rather than op-based: the token captures the
     * pre-mutation ops list plus the generation of that mutation's optimistic update, and
     * any later journal change (mutation, refresh, migration) voids it — a failed write's
     * rollback bumps the generation too, so a rolled-back mutation auto-voids its token.
     * One token covers a whole mutation regardless of how many ops it emitted.
     */
    class UndoToken internal constructor() {
        internal var snapshot: List<Op>? = null
        internal var generation = -1L

        /** Set synchronously by [undo] on the caller's thread, read under [mutex] by
         *  [mutate]: an undo that lands before its mutation has run suppresses the mutation
         *  instead of silently no-oping (GitHub #25 — field-sighted 2026-08-07). Volatile:
         *  written on the main thread, read on [ioDispatcher].
         *  enforced by: UndoIntegrityTest."an undo tapped before the mutate runs suppresses the mutation entirely" */
        @Volatile internal var undoRequested = false
    }

    init {
        refresh()
        scope.launch { playlists.folderTicks.collect { refresh() } }
    }

    fun refresh() {
        scope.launch(ioDispatcher) {
            mutex.withLock {
                // A migration is a journal change, so it voids live tokens ITSELF — the
                // setOps below also bumps, but the error-return between here and there
                // must not leave a pre-migration token live against a post-migration
                // journal: its undo would overwrite the migrated adds, whose pending.csv
                // is already deleted (GitHub #24).
                // enforced by: UndoIntegrityTest."migration voids a live undo token even when the follow-up read fails"
                if (migrateLegacyPending()) generation++
                val text = playlists.readSyncedText(JOURNAL).getOrElse {
                    _errors.trySend(UNAVAILABLE_MESSAGE)
                    return@withLock
                }
                val parsed = text?.let(OpsJournal::parse)
                if (parsed != null && parsed.malformed > 0) {
                    _errors.trySend("Skipped ${parsed.malformed} malformed journal line(s)")
                }
                setOps(OpsJournal.compact(parsed?.ops ?: emptyList()))
            }
        }
    }

    fun toggle(s: RbStation) {
        mutate { ops ->
            if (ops.any { it is Op.Add && it.playlist == null && it.station.url == s.url })
                ops.filterNot { it is Op.Add && it.playlist == null && it.station.url == s.url }
            else ops + Op.Add(newId(), now(), opStation(s))
        }
    }

    fun addTargeted(s: RbStation, playlist: String, group: String) {
        mutate { it + Op.Add(newId(), now(), opStation(s), playlist = playlist, group = group) }
    }

    /** Returns an [UndoToken] synchronously so the caller can offer Undo via [undo];
     *  the token arms with the delete's optimistic update (see [mutate]), so undo works
     *  as soon as the UI reflects the delete; a rolled-back write auto-voids it. */
    fun delete(station: Station): UndoToken {
        val token = UndoToken()
        mutate(armUndo = token) {
            it + Op.Delete(newId(), now(), url = station.url, playlist = station.playlist)
        }
        return token
    }

    /**
     * Moves [station] into [toGroup] of [toPlaylist], returning an [UndoToken] that undoes
     * the whole move. A cross-playlist target emits Delete(source) + Add(target) from a
     * SINGLE [mutate]: one generation bump, one journal write, one token.
     * Two mutates would leave the desktop merge a window in which the station is deleted
     * but not yet added, and the second bump would void the first's token.
     * enforced by: OpsRepositoryMoveTest."cross-playlist move writes the pair in one journal write"
     *              OpsRepositoryMoveTest."cross-playlist move arms one token that undoes both ops"
     *
     * A same-playlist target degrades to a single Move instead: the desktop applies a Move
     * by re-inserting the CSV line verbatim, while an add re-creates it from name/url/icon
     * and drops any further pyradio column.
     * enforced by: OpsRepositoryMoveTest."same-playlist target emits a single move op and no add"
     *
     * The atomicity claimed is over the journal file and the ops StateFlow, under [mutate]'s
     * mutex on [ioDispatcher] — nothing more. The token is handed back synchronously, before
     * that coroutine runs, so this inherits [delete]'s Undo-versus-mutate race unchanged
     * (see [mutate]); the FIFO test dispatcher cannot exercise it and no row claims it does.
     */
    fun moveToPlaylist(station: Station, toPlaylist: String, toGroup: String): UndoToken {
        val token = UndoToken()
        mutate(armUndo = token) { ops ->
            if (toPlaylist == station.playlist)
                ops + Op.Move(newId(), now(), url = station.url, playlist = station.playlist, toGroup = toGroup)
            else ops +
                Op.Delete(newId(), now(), url = station.url, playlist = station.playlist) +
                Op.Add(newId(), now(), opStation(station), playlist = toPlaylist, group = toGroup)
        }
        return token
    }

    /** Restores the pre-delete snapshot iff no journal change intervened since [delete]
     *  (compare-and-swap on [generation] under the mutex); otherwise silently no-ops.
     *  The FIRST act is synchronous: recording the intent on the token, so an undo tapped
     *  before its mutation has run suppresses the mutation in [mutate] rather than racing
     *  it — the tap itself can never be lost (GitHub #25). */
    fun undo(token: UndoToken) {
        token.undoRequested = true
        scope.launch(ioDispatcher) {
            mutex.withLock {
                val snapshot = token.snapshot ?: return@withLock
                if (token.generation != generation) return@withLock
                val before = _ops.value
                setOps(snapshot)
                if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(snapshot))) {
                    setOps(before)
                    _errors.trySend(UNAVAILABLE_MESSAGE)
                }
            }
        }
    }

    private fun mutate(
        armUndo: UndoToken? = null,
        transform: (List<Op>) -> List<Op>,
    ) {
        scope.launch(ioDispatcher) {
            mutex.withLock {
                // An undo already requested for this mutation suppresses it entirely: no
                // optimistic update, no journal write — the mutation never happened, which
                // IS its undo, and a suppressed write is one the desktop can never consume
                // (the 2026-08-07 field loss). Checked under the mutex, so every
                // interleaving of the tap and this coroutine resolves the same way.
                // enforced by: UndoIntegrityTest."an undo tapped before the mutate runs suppresses the mutation entirely"
                if (armUndo?.undoRequested == true) return@withLock
                val before = _ops.value
                val transformed = transform(before)
                // One journal write = one batch id on the ops it introduces (GitHub #38),
                // stamped here — the single write point — so no call site can forget it.
                // Stamp precedes compact: compaction may rewrite or drop ops, and the ops
                // it keeps carry the batch of the write that introduced them.
                // enforced by: OpsRepositoryMoveTest."cross-playlist pair shares one batch id with strictly increasing ts"
                //              OpsRepositoryMoveTest."separate mutates mint distinct batch ids and a single-op mutate still carries one"
                //              OpsJournalTest."compaction's move-rewrites-add keeps the add's batch and takes the move's ts"
                val beforeIds = before.mapTo(HashSet()) { it.id }
                val updated = OpsJournal.compact(
                    // A remove-only transform (un-star) introduces nothing — skip the stamp
                    // rather than mint a uuid and map the list for an identity result.
                    if (transformed.all { it.id in beforeIds }) transformed
                    else OpsJournal.stamp(transformed, priorIds = beforeIds, batch = newId(), baseTs = Instant.now())
                )
                // Optimistic: _ops (and everything derived from it) updates before the SAF
                // write lands; rolled back on write failure so the UI never sticks in a
                // state the journal doesn't back.
                setOps(updated)
                // Arm undo at optimistic-update time: snapshot = pre-mutation ops,
                // generation = the post-bump value. A failed write's rollback setOps bumps
                // the generation again, voiding the token.
                //
                // delete()/moveToPlaylist() hand the token back synchronously, before this
                // coroutine runs, so on the multi-threaded production Dispatchers.IO an
                // Undo tap can reach the mutex first and find snapshot == null — a silent
                // no-op, leaving the delete standing. Fail-safe and sub-millisecond, but
                // real: the tests run on a FIFO StandardTestDispatcher and structurally
                // cannot exercise it.
                armUndo?.let { it.snapshot = before; it.generation = generation }
                if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(updated))) {
                    setOps(before)
                    _errors.trySend(UNAVAILABLE_MESSAGE)
                }
            }
        }
    }

    /** Sole writer of _ops — every change bumps [generation]. Call only under [mutex]. */
    private fun setOps(value: List<Op>) {
        generation++
        _ops.value = value
    }

    /**
     * One-time pending.csv -> null-playlist add ops migration; runs (cheaply, a findFile
     * miss) at the start of every refresh, inside the mutex. A crash between journal write
     * and csv delete re-runs it next refresh: the re-appended adds collapse via dup-add
     * compaction keyed (url, null), so the journal converges and the csv is deleted on the
     * next clean pass.
     */
    /** Returns whether it wrote the journal — the caller owes a generation bump if so. */
    private fun migrateLegacyPending(): Boolean {
        // getOrNull collapses "no csv" (normal) and "folder unreachable" (refresh's own
        // journal read surfaces that error) into skip.
        val csv = playlists.readSyncedText(LEGACY_CSV).getOrNull() ?: return false
        val adds = LegacyPendingMigration.toOps(csv, ids = ::newId, ts = now())
            // The migration is one journal write like any other, so its adds share one batch
            // id — uniformity is what gives an absent field its single meaning (GitHub #38).
            // Nothing is prior: every op is this write's.
            .let { OpsJournal.stamp(it, priorIds = emptySet(), batch = newId(), baseTs = Instant.now()) }
        val existing = playlists.readSyncedText(JOURNAL).getOrElse { return false }
            ?.let { OpsJournal.parse(it).ops } ?: emptyList()
        val merged = OpsJournal.compact(existing + adds)
        if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(merged))) return false
        playlists.deleteSyncedFile(LEGACY_CSV)
        return true
    }

    private fun opStation(s: RbStation) = OpStation(
        name = s.name, url = s.url, icon = s.favicon,
        stationuuid = s.stationuuid, tags = s.tags, country = s.country, votes = s.votes,
    )

    /** Playlist-origin stations carry no radio-browser identity, so stationuuid/tags/
     *  country/votes stay at their OpStation defaults; the desktop merge reads only url,
     *  name and icon from an add. The icon is carried so the M3U tvg-logo survives the move.
     *  enforced by: OpsRepositoryMoveTest."add op carries the playlist station's icon and empty radio-browser extras" */
    private fun opStation(s: Station) = OpStation(name = s.name, url = s.url, icon = s.logo.orEmpty())

    private fun newId() = UUID.randomUUID().toString()

    /** Construction-site ts only: the write path re-mints every new op's ts in
     *  [OpsJournal.stamp] (mutate and the migration both), so these values never reach the
     *  journal — kept as an honest placeholder rather than a sentinel a stamp bypass would
     *  serialize as garbage. */
    private fun now() = Instant.now().toString()

    private companion object {
        const val JOURNAL = "ops.jsonl"
        const val LEGACY_CSV = "pending.csv"
        const val UNAVAILABLE_MESSAGE = "Playlist edits unavailable — synced folder unreachable"
    }
}
