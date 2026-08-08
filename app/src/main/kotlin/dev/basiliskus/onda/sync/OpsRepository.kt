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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Small newest-wins channel (4 slots, DROP_OLDEST), not a replay-less SharedFlow: an
     *  error emitted while no collector exists (Activity recreating) waits for the next one
     *  instead of vanishing — a dropped write-failure was the one report that mattered
     *  (GitHub #30). Four slots, not one: distinct reports in one burst must all survive
     *  (capacity 1 let a fresh error evict an unseen corruption warning — PR #65 round 3)
     *  — yet the bound keeps an outage to seconds of stale snackbars, not the minutes a
     *  64-slot buffer allowed, and a relaunch replays at most four messages.
     *  What this does NOT cover: delivery is at-most-once, so a collector cancelled at
     *  the instant of handover can still consume a message unseen — the recreation window
     *  is narrowed, not closed; a backgrounded app keeps its LaunchedEffect collector,
     *  so a snackbar shown off-screen still times out unseen; and a burst deeper than the
     *  buffer evicts oldest-first — UNDO_VOIDED_MESSAGE shares these slots, accepted
     *  because it fires only on a genuine journal change under an open snackbar (PR #66
     *  review; a no-op tick no longer voids). Single consumer by design (the snackbar host) — a prose
     *  contract; the Flow type cannot enforce it, and a second collector would steal
     *  deliveries.
     *  enforced by: UndoIntegrityTest."an ops error emitted with no collector reaches a late collector"
     *               UndoIntegrityTest."two errors emitted with no collector both reach a late collector" */
    private val _errors = Channel<String>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
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
                // A migration lands in _ops HERE and ends the refresh: `merged` IS the
                // journal this mutex hold just wrote, so the re-read the rest of this
                // function performs could only flake and emit a false unavailable report.
                // The early return deletes the failed-re-read hazard — a stale mirror
                // (or a live pre-migration token) against a post-migration journal, whose
                // undo OR next ordinary mutation would overwrite the migrated adds, their
                // pending.csv already deleted (GitHub #24; the mutate door and the false
                // snackbar are PR #65's review). setOps's generation bump is what voids
                // live tokens.
                // enforced by: UndoIntegrityTest."migration voids a live undo token even when the follow-up read fails"
                //              UndoIntegrityTest."a mutation after a migration with a failed re-read keeps the migrated adds"
                migrateLegacyPending()?.let { setOps(it); return@withLock }
                val text = playlists.readSyncedText(JOURNAL).getOrElse {
                    _errors.trySend(UNAVAILABLE_MESSAGE)
                    return@withLock
                }
                val parsed = text?.let(OpsJournal::parse)
                reportMalformed(parsed?.malformed ?: 0)
                val next = OpsJournal.compact(parsed?.ops ?: emptyList())
                // A content-identical re-read must not bump the generation: every
                // foreground resume ticks the folder watcher even when nothing changed,
                // and an unconditional setOps voided live undo tokens on that no-op
                // refresh — with #18's feedback the tap then reported "Nothing to undo"
                // about a delete that still stood (PR #66 review).
                // Deliberately NOT re-voided on m3u-only changes either: a desktop merge
                // whose re-exported m3u syncs before the journal clears leaves the token
                // live, so an undo inside that window restores a journal whose delete
                // was already consumed and the station stays gone — the pre-existing
                // consumption window (hardware row R1), unreachable under this app's
                // manual-sync workflow, and pre-guard the same tick merely turned it
                // into a voided no-op that restored nothing either (PR #66 round 3).
                // enforced by: UndoIntegrityTest."a content-identical refresh does not void a live undo token"
                if (next != _ops.value) setOps(next)
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
     * that coroutine runs; an Undo tap in that window suppresses the whole mutation via the
     * token's intent flag (GitHub #25 — see [mutate] and [undo]), same as [delete].
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
     *  (compare-and-swap on [generation] under the mutex); a voided tap reports
     *  "Nothing to undo" through [errors] instead of silently no-oping (GitHub #18).
     *  The disarmed-token path stays silent deliberately: snapshot == null means either
     *  the mutation has not run yet (this tap's intent flag suppresses it in [mutate] —
     *  the suppression IS the undo) or an earlier dispatch already restored it (success
     *  disarms the token); neither has anything to report. The FIRST act is
     *  synchronous: recording the intent on the token, so an undo tapped before its
     *  mutation has run suppresses the mutation rather than racing it — the tap can no
     *  longer lose to its own mutation's coroutine (GitHub #25). A tap [mutate]'s flag
     *  check has already passed takes the snapshot path here, and an intervening journal
     *  change still voids the token.
     *  enforced by: UndoIntegrityTest."a voided undo reports Nothing to undo instead of silently no-oping" */
    fun undo(token: UndoToken) {
        token.undoRequested = true
        scope.launch(ioDispatcher) {
            mutex.withLock {
                val snapshot = token.snapshot ?: return@withLock
                if (token.generation != generation) {
                    _errors.trySend(UNDO_VOIDED_MESSAGE)
                    return@withLock
                }
                val before = _ops.value
                setOps(snapshot)
                if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(snapshot))) {
                    setOps(before)
                    // Re-arm: the rollback's own bumps must not void this token — the
                    // undo did NOT happen, so a retry tap must find a live token rather
                    // than a false "Nothing to undo" about a delete that still stands
                    // (PR #66 round 3). Any LATER journal change still voids it.
                    // enforced by: OpsRepositoryUndoTest."failed undo write rolls back to post-delete ops"
                    token.generation = generation
                    _errors.trySend(UNAVAILABLE_MESSAGE)
                } else {
                    // A successful undo consumes the token by disarming it: a redundant
                    // second dispatch (double-tapped snackbar action) takes the silent
                    // snapshot-null exit above — nothing further to undo, nothing to
                    // report — and the retained ops list becomes collectable.
                    // enforced by: OpsRepositoryUndoTest."second undo is a no-op"
                    token.snapshot = null
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
                // (the 2026-08-07 field loss). A tap the flag-write did NOT beat here reads
                // as a late undo: the armed-snapshot path below, two writes, the
                // pre-existing eventual-consistency window included. Suppression narrows
                // that window to the taps that land first; it does not close it — which is
                // why hardware row R1 (fast undo under the journal watcher) stays in the
                // release pass.
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
                // coroutine runs; an Undo tap landing in that window is handled by the
                // undoRequested check at the top of this block (GitHub #25) — the tap sets
                // the flag synchronously, so it either suppresses this mutation or finds
                // the armed snapshot below. The tap can no longer lose the race against
                // its own mutation; an unrelated journal change (a folder-tick refresh,
                // another mutation) can still void the token first, and that voided undo
                // reports "Nothing to undo" (GitHub #18).
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

    /** One phrasing for the skipped-lines report, wherever a parse precedes a path that
     *  rewrites the journal (refresh, migration) — the report outlives the lines. */
    private fun reportMalformed(count: Int) {
        if (count > 0) _errors.trySend("Skipped $count malformed journal line(s)")
    }

    /**
     * One-time pending.csv -> null-playlist add ops migration; runs (cheaply, a findFile
     * miss) at the start of every refresh, inside the mutex. A crash between journal write
     * and csv delete re-runs it next refresh: the re-appended adds collapse via dup-add
     * compaction keyed (url, null), so the journal converges and the csv is deleted on the
     * next clean pass. Returns the merged, compacted ops when it wrote the journal — the
     * caller owes them to [setOps] so the mirror can never go stale against the file —
     * and null when there was nothing to migrate or the write did not land. Malformed
     * journal lines are reported from HERE exactly as refresh reports them: the rewrite
     * below discards them from disk for good, and the refresh that used to report them
     * ends at the migration, so a silent migration would erase the corruption signal
     * together with the lines (PR #65 review).
     */
    private fun migrateLegacyPending(): List<Op>? {
        // getOrNull collapses "no csv" (normal) and "folder unreachable" (refresh's own
        // journal read surfaces that error) into skip.
        val csv = playlists.readSyncedText(LEGACY_CSV).getOrNull() ?: return null
        val adds = LegacyPendingMigration.toOps(csv, ids = ::newId, ts = now())
            // The migration is one journal write like any other, so its adds share one batch
            // id — uniformity is what gives an absent field its single meaning (GitHub #38).
            // Nothing is prior: every op is this write's.
            .let { OpsJournal.stamp(it, priorIds = emptySet(), batch = newId(), baseTs = Instant.now()) }
        val existing = playlists.readSyncedText(JOURNAL).getOrElse { return null }?.let(OpsJournal::parse)
        reportMalformed(existing?.malformed ?: 0)
        val merged = OpsJournal.compact((existing?.ops ?: emptyList()) + adds)
        if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(merged))) return null
        playlists.deleteSyncedFile(LEGACY_CSV)
        return merged
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
        const val UNDO_VOIDED_MESSAGE = "Nothing to undo"
    }
}
