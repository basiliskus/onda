package dev.basiliskus.onda.ui.discover

import dev.basiliskus.onda.core.radiobrowser.RadioBrowserApi
import dev.basiliskus.onda.core.radiobrowser.RbCountry
import dev.basiliskus.onda.core.radiobrowser.RbStation
import dev.basiliskus.onda.core.radiobrowser.RbTag
import dev.basiliskus.onda.core.radiobrowser.StationFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import java.io.IOException
import kotlin.test.*

/** The browse-banner state machine: partial failures keep the banner, a clear needs both
 *  lists, retries refetch only what is missing, and the in-flight guard blocks duplicates.
 *  Pagination: pages append, duplicates are blocked, and a previous list's late page is dropped. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private class FakeRadioBrowser : RadioBrowserApi {
        val tagResults = ArrayDeque<Result<List<RbTag>>>()
        val countryResults = ArrayDeque<Result<List<RbCountry>>>()
        var topTagsCalls = 0
        var countriesCalls = 0
        /** When set, topTags suspends on it — holds a fetch in flight. */
        var tagsGate: CompletableDeferred<Unit>? = null
        /** Same, for the mirrored countries branch. */
        var countriesGate: CompletableDeferred<Unit>? = null

        val searchResults = ArrayDeque<Result<List<RbStation>>>()
        val searchCalls = mutableListOf<Pair<StationFilter, Int>>()
        /** When set, the next searchStations call holds on it and completes even if cancelled —
         *  the real client's blocking OkHttp call is not interrupted by cancellation either. */
        var searchGate: CompletableDeferred<Unit>? = null

        override suspend fun searchStations(filter: StationFilter, offset: Int, limit: Int): List<RbStation> {
            searchCalls += filter to offset
            val result = searchResults.removeFirst()
            val gate = searchGate.also { searchGate = null }
            if (gate != null) withContext(NonCancellable) { gate.await() }
            return result.getOrThrow()
        }

        override suspend fun topTags(limit: Int): List<RbTag> {
            topTagsCalls++
            tagsGate?.await()
            return tagResults.removeFirst().getOrThrow()
        }

        override suspend fun countries(): List<RbCountry> {
            countriesCalls++
            countriesGate?.await()
            return countryResults.removeFirst().getOrThrow()
        }
    }

    private val someTags = listOf(RbTag("jazz", 100))
    private val someCountries = listOf(RbCountry("Uruguay", "UY", 20))
    private val down = Result.failure<Nothing>(IOException("mirror down"))
    private lateinit var client: FakeRadioBrowser
    private lateinit var vm: DiscoverViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client = FakeRadioBrowser()
        vm = DiscoverViewModel(client)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `load success sets both lists and no banner`() = runTest {
        client.tagResults += Result.success(someTags)
        client.countryResults += Result.success(someCountries)
        vm.loadBrowseLists()
        advanceUntilIdle()
        assertEquals(someTags, vm.tags.value)
        assertEquals(someCountries, vm.countries.value)
        assertNull(vm.browseError.value)
    }

    @Test fun `tags failure with countries success keeps the banner`() = runTest {
        client.tagResults += down
        client.countryResults += Result.success(someCountries)
        vm.loadBrowseLists()
        advanceUntilIdle()
        assertNull(vm.tags.value)
        assertEquals(someCountries, vm.countries.value)
        assertEquals("radio-browser unreachable", vm.browseError.value)
    }

    @Test fun `banner clears only when both lists have loaded`() = runTest {
        client.tagResults += down
        client.countryResults += down
        vm.loadBrowseLists()
        advanceUntilIdle()
        assertEquals("radio-browser unreachable", vm.browseError.value)

        client.tagResults += Result.success(someTags)
        client.countryResults += Result.success(someCountries)
        vm.loadBrowseLists()
        advanceUntilIdle()
        assertEquals(someTags, vm.tags.value)
        assertEquals(someCountries, vm.countries.value)
        assertNull(vm.browseError.value)
    }

    @Test fun `retry refetches only the missing list`() = runTest {
        client.tagResults += down
        client.countryResults += Result.success(someCountries)
        vm.loadBrowseLists()
        advanceUntilIdle()

        client.tagResults += Result.success(someTags)
        vm.loadBrowseLists()
        advanceUntilIdle()
        assertEquals(1, client.countriesCalls)
        assertEquals(2, client.topTagsCalls)
        assertEquals(someTags, vm.tags.value)
    }

    @Test fun `re-entry during in-flight fetch does not duplicate it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        client.tagsGate = gate
        client.tagResults += Result.success(someTags)
        client.countryResults += Result.success(someCountries)
        vm.loadBrowseLists()
        advanceUntilIdle() // tags fetch now suspended on the gate
        assertNull(vm.tags.value)

        vm.loadBrowseLists() // re-entry: guard must block a second fetch
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, client.topTagsCalls)
        assertEquals(someTags, vm.tags.value)
        assertNull(vm.browseError.value)
    }

    @Test fun `re-entry during in-flight countries fetch does not duplicate it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        client.countriesGate = gate
        client.tagResults += Result.success(someTags)
        client.countryResults += Result.success(someCountries)
        vm.loadBrowseLists()
        advanceUntilIdle() // countries fetch now suspended on the gate
        assertNull(vm.countries.value)

        vm.loadBrowseLists() // re-entry: guard must block a second fetch
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, client.countriesCalls)
        assertEquals(someCountries, vm.countries.value)
        assertNull(vm.browseError.value)
    }

    @Test fun `guard resets after failure allowing retry`() = runTest {
        client.tagResults += down
        client.countryResults += Result.success(someCountries)
        vm.loadBrowseLists()
        advanceUntilIdle()
        assertEquals(1, client.topTagsCalls)

        client.tagResults += Result.success(someTags)
        vm.loadBrowseLists()
        advanceUntilIdle()
        assertEquals(2, client.topTagsCalls)
        assertEquals(someTags, vm.tags.value)
    }

    private fun stations(prefix: String, n: Int) = List(n) { RbStation("$prefix$it", "$prefix $it", "http://$prefix/$it") }

    @Test fun `pagination appends at the next offset and a short page ends the list`() = runTest {
        val page1 = stations("a", PAGE_SIZE)
        val page2 = stations("b", 5)
        client.searchResults += Result.success(page1)
        client.searchResults += Result.success(page2)
        vm.openList("tag", "jazz")
        advanceUntilIdle()
        assertFalse(vm.list.value.endReached)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(0, PAGE_SIZE), client.searchCalls.map { it.second })
        assertEquals(page1 + page2, vm.list.value.stations)
        assertTrue(vm.list.value.endReached)

        vm.loadMore() // end reached: no further request
        advanceUntilIdle()
        assertEquals(2, client.searchCalls.size)
    }

    @Test fun `two loadMore calls in the same frame fetch the page once`() = runTest {
        client.searchResults += Result.success(stations("a", PAGE_SIZE))
        vm.openList("tag", "jazz")
        advanceUntilIdle()

        client.searchResults += Result.success(stations("b", PAGE_SIZE))
        client.searchResults += Result.success(stations("c", PAGE_SIZE))
        vm.loadMore()
        vm.loadMore() // second scroll callback before the first coroutine runs
        advanceUntilIdle()
        assertEquals(listOf(0, PAGE_SIZE), client.searchCalls.map { it.second })
        assertEquals(2 * PAGE_SIZE, vm.list.value.stations.size)
    }

    @Test fun `late success from the previous list does not overwrite the new one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        client.searchGate = gate
        client.searchResults += Result.success(stations("a", PAGE_SIZE))
        vm.openList("tag", "jazz")
        advanceUntilIdle() // list A's fetch held in flight

        val listB = stations("b", 3)
        client.searchResults += Result.success(listB)
        vm.openList("tag", "rock")
        advanceUntilIdle()
        assertEquals(listB, vm.list.value.stations)

        gate.complete(Unit) // A lands after the switch
        advanceUntilIdle()
        assertEquals(listB, vm.list.value.stations)
        assertFalse(vm.list.value.loading)
    }

    @Test fun `late failure from the previous list does not leak its banner into the new one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        client.searchGate = gate
        client.searchResults += down
        vm.openList("tag", "jazz")
        advanceUntilIdle() // list A's fetch held in flight

        val listB = stations("b", 3)
        client.searchResults += Result.success(listB)
        vm.openList("tag", "rock")
        advanceUntilIdle()

        gate.complete(Unit) // A's failure lands after the switch
        advanceUntilIdle()
        assertEquals(listB, vm.list.value.stations)
        assertNull(vm.list.value.error)
        assertFalse(vm.list.value.loading)
    }
}
