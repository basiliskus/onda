package dev.basiliskus.onda.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import dev.basiliskus.onda.AppGraph
import dev.basiliskus.onda.sync.PlaylistsState

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun OndaRoot(graph: AppGraph) {
    val playlistsState by graph.playlistRepository.state.collectAsState()
    if (playlistsState is PlaylistsState.NeedsFolder) {
        OnboardingScreen(onFolderPicked = graph.playlistRepository::setFolder)
        return
    }
    val nav = rememberNavController()
    val tabs = listOf(
        Tab("playlists", "Playlists", Icons.Filled.LibraryMusic),
        Tab("discover", "Discover", Icons.Filled.TravelExplore),
    )
    Scaffold(
        bottomBar = {
            Column {
                // Mini player slot — filled in Task 11.
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
            composable("playlists") { Text("Playlists — Task 9") }
            composable("discover") { Text("Discover — Task 12") }
        }
    }
}
