package dev.basiliskus.onda.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import dev.basiliskus.onda.AppGraph
import dev.basiliskus.onda.BuildConfig
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.sync.PlaylistsState

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun OndaRoot(graph: AppGraph) {
    val playlistsState by graph.playlistRepository.state.collectAsState()
    if (playlistsState is PlaylistsState.NeedsFolder) {
        OnboardingScreen(onFolderPicked = graph.playlistRepository::setFolder)
        return
    }
    val np by graph.playerConnection.nowPlaying.collectAsState()
    val onPlayStation: (Station) -> Unit = graph.playerConnection::playStation
    val playingUrl: String? = np?.playingUrl
    var showSheet by remember { mutableStateOf(false) }

    val nav = rememberNavController()
    val tabs = listOf(
        Tab("playlists", "Playlists", Icons.Filled.LibraryMusic),
        Tab("discover", "Discover", Icons.Filled.TravelExplore),
    )
    Scaffold(
        bottomBar = {
            Column {
                np?.let { MiniPlayer(it, graph.playerConnection::toggle, { showSheet = true }) }
                NavigationBar {
                    val backStack by nav.currentBackStackEntryAsState()
                    val current = backStack?.destination?.route
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current?.startsWith(tab.route) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "playlists", modifier = Modifier.padding(padding)) {
            composable("playlists") {
                val ready = playlistsState as? PlaylistsState.Ready
                PlaylistsScreen(
                    playlists = ready?.playlists ?: emptyList(),
                    onOpen = { nav.navigate("playlists/$it") },
                    onSettings = { nav.navigate("settings") },
                    onRefresh = graph.playlistRepository::rescan,
                )
            }
            composable("playlists/{name}") { entry ->
                val name = entry.arguments?.getString("name")!!
                val ready = playlistsState as? PlaylistsState.Ready
                val playlist = ready?.playlists?.firstOrNull { it.name == name }
                if (playlist != null) PlaylistDetailScreen(playlist, playingUrl, { nav.popBackStack() }, onPlayStation)
            }
            composable("settings") { SettingsScreen(graph, onBack = { nav.popBackStack() }) }
            composable("discover") { Text("Discover — Task 12") }
        }
    }
    if (showSheet) {
        np?.let { NowPlayingSheet(it, graph.playerConnection::toggle, { showSheet = false }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(graph: AppGraph, onBack: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(graph.playlistRepository::setFolder)
    }
    Column {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        ListItem(
            modifier = Modifier.clickable { picker.launch(null) },
            headlineContent = { Text("Synced folder") },
            supportingContent = { Text(graph.folderPrefs.treeUri?.lastPathSegment ?: "Not set") },
        )
        ListItem(
            headlineContent = { Text("Onda") },
            supportingContent = { Text(BuildConfig.VERSION_NAME) },
        )
    }
}
