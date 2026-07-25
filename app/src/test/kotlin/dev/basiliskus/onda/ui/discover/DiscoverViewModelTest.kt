package dev.basiliskus.onda.ui.discover

import dev.basiliskus.onda.core.radiobrowser.RadioBrowserApi
import dev.basiliskus.onda.core.radiobrowser.RbCountry
import dev.basiliskus.onda.core.radiobrowser.RbStation
import dev.basiliskus.onda.core.radiobrowser.RbTag
import dev.basiliskus.onda.core.radiobrowser.StationFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.IOException
import kotlin.test.*

/** The browse-banner state machine: partial failures keep the banner, a clear needs both
 *  lists, retries refetch only what is missing, and the in-flight guard blocks duplicates. */
class DiscoverViewModelTest {
    private class FakeRadioBrowser : RadioBrowserApi {
        val tagResults = ArrayDeque<Result<List<RbTag>>>()
        val countryResults = ArrayDeque<Result<List<RbCountry>>>()
        var topTagsCalls = 0
        var countriesCalls = 0
        /** When set, topTags suspends on it — holds a fetch in flight. */
        var tagsGate: CompletableDeferred<Unit>? = null

        override suspend fun searchStations(filter: StationFilter, offset: Int, limit: Int): List<RbStation> =
            fail("searchStations not under test")

        override suspend fun topTags(limit: Int): List<RbTag> {
            topTagsCalls++
            tagsGate?.await()
            return tagResults.removeFirst().getOrThrow()
        }

        override suspend fun countries(): List<RbCountry> {
            countriesCalls++
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
}
