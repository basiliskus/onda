package dev.basiliskus.onda.core.model

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ResumptionTest {
    private val station = Station(name = "S", url = "http://u", group = "A", playlist = "stations")
    private val ready: PlaylistsState =
        PlaylistsState.Ready(listOf(Playlist("stations", listOf(Group("A", listOf(station))))))
    private val lastId = MediaIds.station(station)

    @Test fun `resolves immediately when playlists are ready`() = runTest {
        assertEquals(station, resolveResumptionStation(lastId, flowOf(ready)))
    }

    @Test fun `waits through loading then resolves`() = runTest {
        val states = flow {
            emit(PlaylistsState.Loading)
            delay(1_000)
            emit(ready)
        }
        assertEquals(station, resolveResumptionStation(lastId, states))
        assertEquals(1_000, currentTime)
    }

    @Test fun `times out while stuck loading`() = runTest {
        val states = MutableStateFlow<PlaylistsState>(PlaylistsState.Loading)
        assertFailsWith<TimeoutCancellationException> { resolveResumptionStation(lastId, states) }
        assertEquals(3_000, currentTime)
    }

    @Test fun `needs-folder fails with playlists unavailable`() = runTest {
        val e = assertFailsWith<IllegalStateException> {
            resolveResumptionStation(lastId, flowOf(PlaylistsState.NeedsFolder))
        }
        assertEquals("playlists unavailable", e.message)
    }

    @Test fun `null last-played id fails fast`() = runTest {
        val e = assertFailsWith<IllegalStateException> {
            resolveResumptionStation(null, flow { fail("flow must not be collected") })
        }
        assertEquals("no last played station", e.message)
    }

    @Test fun `stale last-played id fails with station no longer in playlists`() = runTest {
        val e = assertFailsWith<IllegalStateException> {
            resolveResumptionStation("station|000000000000", flowOf(ready))
        }
        assertEquals("last played station no longer in playlists", e.message)
    }
}
