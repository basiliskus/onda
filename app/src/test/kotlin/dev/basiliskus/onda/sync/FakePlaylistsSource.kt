package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.model.Group
import dev.basiliskus.onda.core.model.Playlist
import dev.basiliskus.onda.core.model.PlaylistsState
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.ops.Op
import dev.basiliskus.onda.core.ops.OpStation
import dev.basiliskus.onda.core.ops.OpsJournal
import dev.basiliskus.onda.core.radiobrowser.RbStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import java.io.IOException
import java.util.UUID

/** Pure-JVM stand-in for PlaylistRepository behind the [PlaylistsSource] seam: playlist
 *  state and the synced folder collapse to a StateFlow plus an in-memory file map, with
 *  read/write failure injection replacing SAF fault conditions. */
internal class FakePlaylistsSource(
    initial: PlaylistsState = PlaylistsState.Loading,
) : PlaylistsSource {
    override val state = MutableStateFlow(initial)
    override val folderTicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val files = mutableMapOf<String, String>()
    var failReads = false
    var failWrites = false
    var failAppends = false
    /** Write attempts, failed ones included — lets a row assert that a path which leaves
     *  the journal unchanged did so by not writing, rather than by rewriting the same
     *  bytes (the only way to observe a no-op restore). */
    var writes = 0
    /** Append attempts, failed ones included — same reason as [writes]: it lets a row assert
     *  a path wrote nothing by not attempting, rather than by writing identical bytes. */
    var appends = 0

    override fun readSyncedText(name: String): Result<String?> =
        if (failReads) Result.failure(IOException("synced folder unreachable"))
        else Result.success(files[name])

    override fun writeSyncedText(name: String, text: String): Boolean {
        writes++
        if (failWrites) return false
        files[name] = text
        return true
    }

    override fun deleteSyncedFile(name: String): Boolean {
        files.remove(name)
        return true
    }

    /** The real seam creates recordings/onda-titles.log itself; keying the flat file map by
     *  that path is what makes "capture touched nothing else" observable here. */
    override fun appendTitlesLogLine(line: String): Boolean {
        appends++
        if (failAppends) return false
        files[TITLES_LOG_PATH] = (files[TITLES_LOG_PATH] ?: "") + line + "\n"
        return true
    }

    fun seedJournal(vararg ops: Op) {
        files[JOURNAL] = OpsJournal.serialize(ops.toList())
    }

    /** The persisted journal, round-tripped through the production parser. */
    fun journalOps(): List<Op> = OpsJournal.parse(files[JOURNAL] ?: "").ops

    /** The titles log's records, without the empty tail the trailing newline produces. */
    fun titlesLogLines(): List<String> = files[TITLES_LOG_PATH]?.lines().orEmpty().dropLast(1)
}

/** Deliberate golden copies of OpsRepository's and PlaylistRepository's private constants —
 *  changing a filename or a user-visible string there must fail these tests rather than
 *  silently follow along. */
internal const val JOURNAL = "ops.jsonl"
internal const val LEGACY_CSV = "pending.csv"
internal const val UNAVAILABLE_MESSAGE = "Playlist edits unavailable — synced folder unreachable"
internal const val TITLES_LOG_PATH = "recordings/onda-titles.log"

/** Scope for the repo's never-completing flows, auto-cancelled at test end via the
 *  background job — but dispatched as FOREGROUND tasks: advanceUntilIdle stops once only
 *  backgroundScope-launched tasks remain, so launching the repo there would never drain. */
internal fun TestScope.repoScope(): CoroutineScope =
    CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext.job))

internal fun TestScope.repo(fake: FakePlaylistsSource): OpsRepository =
    OpsRepository(fake, repoScope(), StandardTestDispatcher(testScheduler))

/** Start collecting repo errors before acting — the error flow has no replay, so a late
 *  collector would miss emissions (UNDISPATCHED subscribes before any queued coroutine runs). */
internal fun TestScope.collectErrors(repo: OpsRepository): List<String> {
    val errors = mutableListOf<String>()
    repoScope().launch(start = CoroutineStart.UNDISPATCHED) { repo.errors.collect { errors += it } }
    return errors
}

internal fun station(url: String, playlist: String = "stations", group: String = "A") =
    Station(name = "S $url", url = url, group = group, playlist = playlist)

internal fun rbStation(url: String) = RbStation(stationuuid = "uuid-$url", name = "S $url", url = url)

/** Ready state grouping [stations] by playlist/group; with none, a single empty "stations"
 *  playlist (it must exist for targeted adds to surface — applyOps never creates playlists). */
internal fun ready(vararg stations: Station): PlaylistsState.Ready = PlaylistsState.Ready(
    stations.groupBy { it.playlist }
        .map { (name, inPlaylist) ->
            Playlist(name, inPlaylist.groupBy { it.group }.map { (title, ss) -> Group(title, ss) })
        }
        .ifEmpty { listOf(Playlist("stations", emptyList())) }
)

internal fun OpsRepository.effectiveStations(): List<Station> =
    (effectivePlaylists.value as PlaylistsState.Ready).playlists.flatMap { it.stations }

private fun nextId() = "id-${UUID.randomUUID()}"
private const val TS = "2026-07-25T00:00:00Z"

internal fun addOp(url: String, playlist: String? = null, group: String? = null) =
    Op.Add(nextId(), TS, OpStation(name = "S $url", url = url), playlist, group)

internal fun deleteOp(url: String, playlist: String) = Op.Delete(nextId(), TS, url, playlist)

internal fun moveOp(url: String, playlist: String, toGroup: String) =
    Op.Move(nextId(), TS, url, playlist, toGroup)
