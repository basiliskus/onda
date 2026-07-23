package dev.basiliskus.onda.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.basiliskus.onda.core.model.Playlist
import dev.basiliskus.onda.core.model.Station

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    playingUrl: String?,
    onBack: () -> Unit,
    onPlayStation: (Station) -> Unit,
) {
    Column {
        TopAppBar(
            title = { Text(playlist.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        LazyColumn {
            playlist.groups.forEach { group ->
                stickyHeader(key = "h:${group.title}") {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(group.stations, key = { "${group.title}|${it.url}" }) { s ->
                    StationRow(
                        name = s.name, subtitle = null, logoUrl = s.logo,
                        isPlaying = s.url == playingUrl,
                        onClick = { onPlayStation(s) },
                    )
                }
            }
        }
    }
}
