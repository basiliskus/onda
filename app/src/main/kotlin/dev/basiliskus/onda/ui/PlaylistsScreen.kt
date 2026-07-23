package dev.basiliskus.onda.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import dev.basiliskus.onda.core.model.Playlist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column {
        TopAppBar(
            title = { Text("Playlists") },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") } },
        )
        var refreshing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                onRefresh()
                scope.launch { delay(600); refreshing = false }
            },
        ) {
            LazyColumn {
                items(playlists, key = { it.name }) { p ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(p.name) },
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.stations.size} stations · ${p.groups.size} groups") },
                    )
                }
            }
        }
    }
}
