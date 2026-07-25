package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.model.PlaylistsState
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.ops.LegacyPendingMigration
import dev.basiliskus.onda.core.ops.Op
import dev.basiliskus.onda.core.ops.OpStation
import dev.basiliskus.onda.core.ops.OpsJournal
import dev.basiliskus.onda.core.ops.applyOps
import dev.basiliskus.onda.core.radiobrowser.RbStation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

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
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors
    private val mutex = Mutex()
    /** Bumped (under [mutex]) on every _ops change; guards [UndoToken] validity. */
    private var generation = 0L

    /**
     * Opaque handle returned by [delete] for [undo]. Compaction can consume the delete op
     * itself (deleting a pending add, or a just-moved row), so undo is snapshot-based: the
     * token captures the pre-delete ops list plus the generation of the delete's optimistic
     * update, and any later journal change (mutation, refresh, migration) voids it — a
     * failed write's rollback bumps the generation too, so a rolled-back delete auto-voids
     * its token.
     */
    class UndoToken internal constructor() {
        internal var snapshot: List<Op>? = null
        internal var generation = -1L
    }

    init {
        refresh()
        scope.launch { playlists.folderTicks.collect { refresh() } }
    }

    fun refresh() {
        scope.launch(ioDispatcher) {
            mutex.withLock {
                migrateLegacyPending()
                val text = playlists.readSyncedText(JOURNAL).getOrElse {
                    _errors.tryEmit(UNAVAILABLE_MESSAGE)
                    return@withLock
                }
                val parsed = text?.let(OpsJournal::parse)
                if (parsed != null && parsed.malformed > 0) {
                    _errors.tryEmit("Skipped ${parsed.malformed} malformed journal line(s)")
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

    fun move(station: Station, toGroup: String) {
        mutate { it + Op.Move(newId(), now(), url = station.url, playlist = station.playlist, toGroup = toGroup) }
    }

    /** Restores the pre-delete snapshot iff no journal change intervened since [delete]
     *  (compare-and-swap on [generation] under the mutex); otherwise silently no-ops. */
    fun undo(token: UndoToken) {
        scope.launch(ioDispatcher) {
            mutex.withLock {
                val snapshot = token.snapshot ?: return@withLock
                if (token.generation != generation) return@withLock
                val before = _ops.value
                setOps(snapshot)
                if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(snapshot))) {
                    setOps(before)
                    _errors.tryEmit(UNAVAILABLE_MESSAGE)
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
                val before = _ops.value
                val updated = OpsJournal.compact(transform(before))
                // Optimistic: _ops (and everything derived from it) updates before the SAF
                // write lands; rolled back on write failure so the UI never sticks in a
                // state the journal doesn't back.
                setOps(updated)
                // Arm undo at optimistic-update time: snapshot = pre-mutation ops,
                // generation = the post-bump value. The mutex is what orders undo against
                // this mutation — an undo() launched by the caller queues behind the whole
                // block — so the token is always armed by the time it can be redeemed. A
                // failed write's rollback setOps bumps the generation again, voiding it.
                armUndo?.let { it.snapshot = before; it.generation = generation }
                if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(updated))) {
                    setOps(before)
                    _errors.tryEmit(UNAVAILABLE_MESSAGE)
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
    private fun migrateLegacyPending() {
        // getOrNull collapses "no csv" (normal) and "folder unreachable" (refresh's own
        // journal read surfaces that error) into skip.
        val csv = playlists.readSyncedText(LEGACY_CSV).getOrNull() ?: return
        val adds = LegacyPendingMigration.toOps(csv, ids = ::newId, ts = now())
        val existing = playlists.readSyncedText(JOURNAL).getOrElse { return }
            ?.let { OpsJournal.parse(it).ops } ?: emptyList()
        val merged = OpsJournal.compact(existing + adds)
        if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(merged))) return
        playlists.deleteSyncedFile(LEGACY_CSV)
    }

    private fun opStation(s: RbStation) = OpStation(
        name = s.name, url = s.url, icon = s.favicon,
        stationuuid = s.stationuuid, tags = s.tags, country = s.country, votes = s.votes,
    )

    private fun newId() = UUID.randomUUID().toString()
    private fun now() = Instant.now().toString()

    private companion object {
        const val JOURNAL = "ops.jsonl"
        const val LEGACY_CSV = "pending.csv"
        const val UNAVAILABLE_MESSAGE = "Playlist edits unavailable — synced folder unreachable"
    }
}
