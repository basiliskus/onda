package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.ops.LegacyPendingMigration
import dev.basiliskus.onda.core.ops.Op
import dev.basiliskus.onda.core.ops.OpStation
import dev.basiliskus.onda.core.ops.OpsJournal
import dev.basiliskus.onda.core.radiobrowser.RbStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val playlists: PlaylistRepository,
    private val scope: CoroutineScope,
) {
    private val _ops = MutableStateFlow<List<Op>>(emptyList())
    val ops: StateFlow<List<Op>> = _ops
    /** Urls with an unconsumed add op (quick or targeted) — drives the Discover star. */
    val starredUrls: StateFlow<Set<String>> = _ops
        .map { ops -> ops.filterIsInstance<Op.Add>().map { it.station.url }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors
    private val mutex = Mutex()

    init {
        refresh()
        scope.launch { playlists.folderTicks.collect { refresh() } }
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
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
                _ops.value = OpsJournal.compact(parsed?.ops ?: emptyList())
            }
        }
    }

    fun toggle(s: RbStation) {
        mutate { ops ->
            if (ops.any { it is Op.Add && it.station.url == s.url })
                ops.filterNot { it is Op.Add && it.station.url == s.url }
            else ops + Op.Add(newId(), now(), opStation(s))
        }
    }

    fun addTargeted(s: RbStation, playlist: String, group: String) {
        mutate { it + Op.Add(newId(), now(), opStation(s), playlist = playlist, group = group) }
    }

    /** Returns the delete op's id synchronously so the caller can offer Undo via [removeOp]. */
    fun delete(station: Station): String {
        val id = newId()
        mutate { it + Op.Delete(id, now(), url = station.url, playlist = station.playlist) }
        return id
    }

    fun move(station: Station, toGroup: String) {
        mutate { it + Op.Move(newId(), now(), url = station.url, playlist = station.playlist, toGroup = toGroup) }
    }

    /** Undo / un-star primitive: the op never happened. Unknown id (e.g. after a rolled-back
     *  write) leaves the list unchanged — the identical rewrite is harmless. */
    fun removeOp(id: String) {
        mutate { OpsJournal.removeOp(it, id) }
    }

    private fun mutate(transform: (List<Op>) -> List<Op>) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val before = _ops.value
                val updated = OpsJournal.compact(transform(before))
                // Optimistic: _ops (and everything derived from it) updates before the SAF
                // write lands; rolled back on write failure so the UI never sticks in a
                // state the journal doesn't back.
                _ops.value = updated
                if (!playlists.writeSyncedText(JOURNAL, OpsJournal.serialize(updated))) {
                    _ops.value = before
                    _errors.tryEmit(UNAVAILABLE_MESSAGE)
                }
            }
        }
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
