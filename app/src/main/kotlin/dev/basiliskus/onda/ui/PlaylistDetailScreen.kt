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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    // Sheet state survives rotation and process death as station URLS, never snapshots
    // (GitHub #18; PR #66 review): the live Station is re-derived from the current
    // playlist on every composition, so a station that vanished while the app was dead
    // dismisses its sheet instead of completing a move against a stale world — url is
    // station identity within one playlist, the same key delete/move ops use.
    var actionsForUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var movingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    // Two-step cross-playlist flow: pick the playlist, then a group inside it.
    var crossMoveUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var crossMoveTarget by rememberSaveable { mutableStateOf<String?>(null) }
    fun stationOf(url: String?): Station? = url?.let { u -> playlist.stations.firstOrNull { it.url == u } }
    val actionsFor = stationOf(actionsForUrl)
    val movingStation = stationOf(movingUrl)
    val crossMoveStation = stationOf(crossMoveUrl)
    // A url whose station is gone (or a target playlist that vanished) is cleared, not
    // merely hidden — leaving it set would pop the sheet back open if a later sync
    // re-created the station under the same url.
    LaunchedEffect(playlist, playlistNames) {
        if (actionsForUrl != null && actionsFor == null) actionsForUrl = null
        if (movingUrl != null && movingStation == null) movingUrl = null
        if (crossMoveUrl != null && crossMoveStation == null) { crossMoveUrl = null; crossMoveTarget = null }
        if (crossMoveTarget != null && crossMoveTarget !in playlistNames) crossMoveTarget = null
    }
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
                    DismissibleStationRow(s, playingUrl, onPlayStation, onDeleteStation, onLongPress = { actionsForUrl = s.url })
                }
            }
        }
    }
    actionsFor?.let { s ->
        StationActionsSheet(
            stationName = s.name,
            onMove = { actionsForUrl = null; movingUrl = s.url },
            onMoveToPlaylist = { actionsForUrl = null; crossMoveUrl = s.url },
            onRemove = { actionsForUrl = null; onDeleteStation(s) },
            onDismiss = { actionsForUrl = null },
        )
    }
    movingStation?.let { s ->
        GroupPickerSheet(
            title = "Move to group",
            options = playlist.groups.map { it.title },
            // Same callback as the cross-playlist flow, with the station's own playlist as the
            // target: one entry point, so a group move gets the same Undo affordance.
            onPick = { group ->
                movingUrl = null
                if (group != s.group) onMoveToPlaylist(s, s.playlist, group)
            },
            onDismiss = { movingUrl = null },
            newOptionLabel = "New group",
        )
    }
    crossMoveStation?.let { s ->
        // takeIf, not trust: a restored target naming a vanished playlist would emit an
        // Add no playlist file can apply — the picker steps back instead (PR #66 review).
        val target = crossMoveTarget?.takeIf { it in playlistNames }
        if (target == null) {
            GroupPickerSheet(
                title = "Move to playlist",
                // No newOptionLabel: playlists are files the app cannot create.
                options = playlistNames,
                onPick = { crossMoveTarget = it },
                onDismiss = { crossMoveUrl = null; crossMoveTarget = null },
            )
        } else {
            GroupPickerSheet(
                title = "Move to group in $target",
                options = groupsOf(target),
                onPick = { group ->
                    // Clear before the callback: the snackbar draws where the sheet sits.
                    crossMoveUrl = null
                    crossMoveTarget = null
                    if (target != s.playlist || group != s.group) onMoveToPlaylist(s, target, group)
                },
                onDismiss = { crossMoveUrl = null; crossMoveTarget = null },
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
