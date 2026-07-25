package dev.basiliskus.onda.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    onDeleteStation: (Station) -> Unit,
    playlistNames: List<String>,
    groupsOf: (String) -> List<String>,
    onMoveToPlaylist: (Station, String, String) -> Unit,
) {
    var actionsFor by remember { mutableStateOf<Station?>(null) }
    var movingStation by remember { mutableStateOf<Station?>(null) }
    // Two-step cross-playlist flow: pick the playlist, then a group inside it. Plain
    // remember like the three sibling flows, so rotation drops it the same way (GitHub #18).
    var crossMoveStation by remember { mutableStateOf<Station?>(null) }
    var crossMoveTarget by remember { mutableStateOf<String?>(null) }
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
                itemsIndexed(group.stations, key = { i, s -> "$i:${group.title}|${s.url}" }) { _, s ->
                    DismissibleStationRow(s, playingUrl, onPlayStation, onDeleteStation, onLongPress = { actionsFor = s })
                }
            }
        }
    }
    actionsFor?.let { s ->
        StationActionsSheet(
            stationName = s.name,
            onMove = { actionsFor = null; movingStation = s },
            onMoveToPlaylist = { actionsFor = null; crossMoveStation = s },
            onRemove = { actionsFor = null; onDeleteStation(s) },
            onDismiss = { actionsFor = null },
        )
    }
    movingStation?.let { s ->
        GroupPickerSheet(
            title = "Move to group",
            options = playlist.groups.map { it.title },
            // Same callback as the cross-playlist flow, with the station's own playlist as the
            // target: one entry point, so a group move gets the same Undo affordance.
            onPick = { group ->
                movingStation = null
                if (group != s.group) onMoveToPlaylist(s, s.playlist, group)
            },
            onDismiss = { movingStation = null },
            newOptionLabel = "New group",
        )
    }
    crossMoveStation?.let { s ->
        val target = crossMoveTarget
        if (target == null) {
            GroupPickerSheet(
                title = "Move to playlist",
                // No newOptionLabel: playlists are files the app cannot create.
                options = playlistNames,
                onPick = { crossMoveTarget = it },
                onDismiss = { crossMoveStation = null },
            )
        } else {
            GroupPickerSheet(
                title = "Move to group in $target",
                options = groupsOf(target),
                onPick = { group ->
                    // Clear before the callback: the snackbar draws where the sheet sits.
                    crossMoveStation = null
                    crossMoveTarget = null
                    if (target != s.playlist || group != s.group) onMoveToPlaylist(s, target, group)
                },
                onDismiss = { crossMoveStation = null; crossMoveTarget = null },
                newOptionLabel = "New group",
            )
        }
    }
}

@Composable
private fun DismissibleStationRow(
    s: Station,
    playingUrl: String?,
    onPlayStation: (Station) -> Unit,
    onDeleteStation: (Station) -> Unit,
    onLongPress: () -> Unit,
) {
    // Deliberately NOT rememberSwipeToDismissBoxState: that state is saveable, and
    // LazyColumn's saveable state holder would restore the dismissed value when an
    // undone row re-enters under the same key, leaving it stuck dismissed. Plain
    // remember is discarded with the row, so undo re-inserts a settled row.
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold) }
    SwipeToDismissBox(
        state = dismissState,
        onDismiss = { onDeleteStation(s) },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        StationRow(
            name = s.name, subtitle = null, logoUrl = s.logo,
            isPlaying = s.url == playingUrl,
            onClick = { onPlayStation(s) },
            pending = s.pending,
            onLongClick = onLongPress,
        )
    }
}
