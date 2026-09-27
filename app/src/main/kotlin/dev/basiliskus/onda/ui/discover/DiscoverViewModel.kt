package dev.basiliskus.onda.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.basiliskus.onda.core.radiobrowser.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

const val PAGE_SIZE = 30

data class StationListState(
    val stations: List<RbStation> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

class DiscoverViewModel(private val client: RadioBrowserApi) : ViewModel() {
    val tags = MutableStateFlow<List<RbTag>?>(null)
    val countries = MutableStateFlow<List<RbCountry>?>(null)
    val browseError = MutableStateFlow<String?>(null)

    private val _list = MutableStateFlow(StationListState())
    val list: StateFlow<StationListState> = _list
    private var filter = StationFilter()
    // Bumped per openList. A previous list's requests remain active; a page fetch
    // writes its result only if its generation is still current.
    // enforced by: DiscoverViewModelTest."late success from the previous list does not overwrite the new one"
    // enforced by: DiscoverViewModelTest."late failure from the previous list does not leak its banner into the new one"
    // enforced by: DiscoverViewModelTest."late next page from the previous list does not change the new one"
    private var listGeneration = 0

    // In-flight guards: re-entering the screen mid-fetch must not launch a
    // duplicate whose late failure could set a banner no retry would clear.
    private var tagsLoading = false
    private var countriesLoading = false

    fun loadBrowseLists() {
        if (tags.value == null && !tagsLoading) {
            tagsLoading = true
            viewModelScope.launch {
                try {
                    tags.value = client.topTags()
                    clearBrowseErrorIfLoaded()
                } catch (e: IOException) {
                    browseError.value = "radio-browser unreachable"
                } finally { tagsLoading = false }
            }
        }
        if (countries.value == null && !countriesLoading) {
            countriesLoading = true
            viewModelScope.launch {
                try {
                    countries.value = client.countries()
                    clearBrowseErrorIfLoaded()
                } catch (e: IOException) {
                    browseError.value = "radio-browser unreachable"
                } finally { countriesLoading = false }
            }
        }
    }

    // Both fetches run concurrently and either can fail alone; a partial
    // failure must keep the banner up.
    private fun clearBrowseErrorIfLoaded() {
        if (tags.value != null && countries.value != null) browseError.value = null
    }

    fun openList(kind: String, value: String) {
        val newFilter = when (kind) {
            "tag" -> StationFilter(tag = value)
            "country" -> StationFilter(countrycode = value)
            "search" -> StationFilter(name = value)
            else -> StationFilter() // top: no filter, votes ordering does the work
        }
        if (newFilter == filter && _list.value.stations.isNotEmpty()) return
        filter = newFilter
        listGeneration++
        _list.value = StationListState(loading = true)
        loadMore(reset = true)
    }

    fun loadMore(reset: Boolean = false) {
        val cur = _list.value
        if (!reset && (cur.loading || cur.endReached)) return
        // Set synchronously so a second call in the same frame sees the guard.
        // enforced by: DiscoverViewModelTest."two loadMore calls in the same frame fetch the page once"
        _list.value = cur.copy(loading = true, error = null)
        val generation = listGeneration
        viewModelScope.launch {
            val next = try {
                val offset = if (reset) 0 else cur.stations.size
                val page = client.searchStations(filter, offset = offset, limit = PAGE_SIZE)
                StationListState(
                    stations = (if (reset) emptyList() else cur.stations) + page,
                    endReached = page.size < PAGE_SIZE,
                )
            } catch (e: IOException) {
                cur.copy(loading = false, error = "radio-browser unreachable")
            }
            if (generation == listGeneration) _list.value = next
        }
    }
}
