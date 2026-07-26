package dev.basiliskus.onda.core.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ResumptionTest {
    private val station = Station(name = "S", url = "http://u", group = "A", playlist = "stations")
    private val ready: PlaylistsState =
        PlaylistsState.Ready(listOf(Playlist("stations", listOf(Group("A", listOf(station))))))
    private val lastId = MediaIds.station(station)

    @Test fun `resolves immediately when playlists are ready`() = runTest {
        assertEquals(station, resolveResumptionStation(lastId, null, flowOf(ready)))
    }

    @Test fun `waits through loading then resolves`() = runTest {
        val states = flow {
            emit(PlaylistsState.Loading)
            delay(1_000)
            emit(ready)
        }
        assertEquals(station, resolveResumptionStation(lastId, null, states))
        assertEquals(1_000, currentTime)
    }

    @Test fun `times out while stuck loading`() = runTest {
        val states = MutableStateFlow<PlaylistsState>(PlaylistsState.Loading)
        assertFailsWith<TimeoutCancellationException> { resolveResumptionStation(lastId, null, states) }
        assertEquals(3_000, currentTime)
    }

    @Test fun `needs-folder fails with playlists unavailable`() = runTest {
        val e = assertFailsWith<IllegalStateException> {
            resolveResumptionStation(lastId, null, flowOf(PlaylistsState.NeedsFolder))
        }
        assertEquals("playlists unavailable", e.message)
    }

    @Test fun `null last-played id fails fast`() = runTest {
        val e = assertFailsWith<IllegalStateException> {
            resolveResumptionStation(null, null, flow { fail("flow must not be collected") })
        }
        assertEquals("no last played station", e.message)
    }

    @Test fun `stale last-played id fails with station no longer in playlists`() = runTest {
        val e = assertFailsWith<IllegalStateException> {
            resolveResumptionStation("station|000000000000", null, flowOf(ready))
        }
        assertEquals("last played station no longer in playlists", e.message)
    }

    // GitHub #35 — the id hashes playlist and group, so both of these moves invalidate it.
    // `lastId` is captured above from the station's ORIGINAL location, which is exactly what
    // a real install has persisted when the move happens.

    @Test fun `a station moved to another group still resumes`() = runTest {
        val moved = station.copy(group = "B")
        val after = PlaylistsState.Ready(listOf(Playlist("stations", listOf(Group("B", listOf(moved))))))
        assertNotEquals(lastId, MediaIds.station(moved))
        assertEquals(moved, resolveResumptionStation(lastId, station.url, flowOf(after)))
    }

    @Test fun `a station moved to another playlist still resumes`() = runTest {
        val moved = station.copy(playlist = "nts", group = "Live")
        val after = PlaylistsState.Ready(listOf(Playlist("nts", listOf(Group("Live", listOf(moved))))))
        assertNotEquals(lastId, MediaIds.station(moved))
        assertEquals(moved, resolveResumptionStation(lastId, station.url, flowOf(after)))
    }

    /**
     * The id path must win outright, not merely agree: the url here belongs to a DIFFERENT
     * station, so consulting it at all would return the wrong one.
     */
    @Test fun `an unmoved station resolves by id and never consults the url`() = runTest {
        val other = Station(name = "T", url = "http://other", group = "A", playlist = "stations")
        val both = PlaylistsState.Ready(
            listOf(Playlist("stations", listOf(Group("A", listOf(other, station))))),
        )
        assertEquals(station, resolveResumptionStation(lastId, other.url, flowOf(both)))
    }

    @Test fun `a null url leaves a stale id failing as it always did`() = runTest {
        val moved = station.copy(group = "B")
        val after = PlaylistsState.Ready(listOf(Playlist("stations", listOf(Group("B", listOf(moved))))))
        val e = assertFailsWith<IllegalStateException> {
            resolveResumptionStation(lastId, null, flowOf(after))
        }
        assertEquals("last played station no longer in playlists", e.message)
    }
}
