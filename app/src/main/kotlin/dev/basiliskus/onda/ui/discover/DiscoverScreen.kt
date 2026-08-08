package dev.basiliskus.onda.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TOP_INDEX = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(vm: DiscoverViewModel, onOpenList: (kind: String, value: String) -> Unit) {
    LaunchedEffect(Unit) { vm.loadBrowseLists() }
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val tags by vm.tags.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val browseError by vm.browseError.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search stations") },
            trailingIcon = {
                IconButton(onClick = { if (query.isNotBlank()) onOpenList("search", query.trim()) }) {
                    Icon(Icons.Filled.Search, "Search")
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 16.dp)) {
            listOf("Tags", "Countries", "Top voted").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i, onClick = { mode = i; if (i == TOP_INDEX) onOpenList("top", "-") },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(label) }
            }
        }
        browseError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        when (mode) {
            // Navigation happens in the segment's onClick; a restored Top
            // selection must render content, not re-navigate from composition.
            TOP_INDEX -> ListItem(
                modifier = Modifier.clickable { onOpenList("top", "-") },
                headlineContent = { Text("Top voted stations") },
            )
            0 -> LazyColumn {
                items(tags.orEmpty(), key = { it.name }) { t ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenList("tag", t.name) },
                        headlineContent = { Text(t.name) },
                        trailingContent = { Text("${t.stationcount}") },
                    )
                }
            }
            1 -> LazyColumn {
                items(countries.orEmpty(), key = { it.iso_3166_1 }) { c ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenList("country", c.iso_3166_1) },
                        headlineContent = { Text(c.name) },
                        trailingContent = { Text("${c.stationcount}") },
                    )
                }
            }
        }
    }
}
