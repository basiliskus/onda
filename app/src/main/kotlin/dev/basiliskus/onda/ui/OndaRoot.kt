package dev.basiliskus.onda.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.basiliskus.onda.AppGraph
import dev.basiliskus.onda.BuildConfig
import dev.basiliskus.onda.core.model.PlaylistsState
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.radiobrowser.RbStation
import dev.basiliskus.onda.ui.discover.DiscoverScreen
import dev.basiliskus.onda.ui.discover.DiscoverViewModel
import dev.basiliskus.onda.ui.discover.StationListScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OndaRoot(graph: AppGraph) {
    val playlistsState by graph.opsRepository.effectivePlaylists.collectAsStateWithLifecycle()
    if (playlistsState is PlaylistsState.NeedsFolder) {
        OnboardingScreen(onFolderPicked = graph.playlistRepository::setFolder)
        return
    }
    val np by graph.playerConnection.nowPlaying.collectAsStateWithLifecycle()
    val onPlayStation: (Station) -> Unit = graph.playerConnection::playStation
    val playingUrl: String? = np?.playingUrl
    var showSheet by remember { mutableStateOf(false) }
    LaunchedEffect(np) { if (np == null) showSheet = false }
    val discoverVm: DiscoverViewModel = viewModel { DiscoverViewModel(graph.radioBrowser) }

    val nav = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        graph.opsRepository.errors.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    val uiScope = rememberCoroutineScope()
    val onDeleteStation: (Station) -> Unit = { s ->
        // delete() hands back an undo token synchronously, so the snackbar can show
        // immediately; undo() restores the pre-delete journal snapshot (and no-ops if a
        // later journal change already voided it).
        val undoToken = graph.opsRepository.delete(s)
        uiScope.launch {
            val result = snackbarHostState.showSnackbar(
                "Removed ${s.name}", actionLabel = "Undo", duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) graph.opsRepository.undo(undoToken)
        }
    }
    val tabs = listOf(
        Tab("playlists", "Playlists", Icons.Filled.LibraryMusic),
        Tab("discover", "Discover", Icons.Filled.TravelExplore),
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                val scanning by graph.playlistRepository.scanning.collectAsStateWithLifecycle()
                PlaylistsScreen(
                    playlists = ready?.playlists ?: emptyList(),
                    scanning = scanning,
                    onOpen = { nav.navigate("playlists/${android.net.Uri.encode(it)}") },
                    onSettings = { nav.navigate("settings") },
                    onRefresh = graph.playlistRepository::rescan,
                )
            }
            composable("playlists/{name}") { entry ->
                val name = entry.arguments?.getString("name")!!
                val ready = playlistsState as? PlaylistsState.Ready
                val playlist = ready?.playlists?.firstOrNull { it.name == name }
                // A playlist can vanish out from under this screen (file deleted/renamed by
                // sync) — without this, the screen goes blank and strands the user there.
                // Only pop once the repository has actually settled on Ready and confirmed
                // the playlist is gone; while still Loading, ready == null must not read as
                // "vanished" (that would pop immediately on process-death restore).
                val vanished = ready != null && playlist == null
                LaunchedEffect(vanished) { if (vanished) nav.popBackStack() }
                if (playlist != null) {
                    PlaylistDetailScreen(
                        playlist, playingUrl, { nav.popBackStack() }, onPlayStation, onDeleteStation,
                        onMoveStation = graph.opsRepository::move,
                    )
                }
            }
            composable("settings") { SettingsScreen(graph, onBack = { nav.popBackStack() }) }
            composable("discover") {
                DiscoverScreen(discoverVm) { kind, value ->
                    nav.navigate("discover/stations/$kind/${android.net.Uri.encode(value)}")
                }
            }
            composable("discover/stations/{kind}/{value}") { entry ->
                val kind = entry.arguments?.getString("kind")!!
                val value = entry.arguments?.getString("value")!!
                LaunchedEffect(kind, value) { discoverVm.openList(kind, value) }
                val starredUrls by graph.opsRepository.starredUrls.collectAsStateWithLifecycle()
                var addTarget by remember { mutableStateOf<RbStation?>(null) }
                var addPlaylist by remember { mutableStateOf<String?>(null) }
                StationListScreen(
                    title = if (kind == "top") "Top voted" else value,
                    vm = discoverVm,
                    playingUrl = np?.playingUrl,
                    onBack = { nav.popBackStack() },
                    onPlay = graph.playerConnection::playRb,
                    trailing = { s ->
                        val starred = s.url in starredUrls
                        // IconButton has no long-press slot, so the star is a Box with
                        // combinedClickable: tap toggles the quick star, long-press opens
                        // the targeted-add picker (a TalkBack custom action).
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { graph.opsRepository.toggle(s) },
                                    onLongClickLabel = "Add to playlist",
                                    onLongClick = { addTarget = s; addPlaylist = null },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                                if (starred) "Unstar station" else "Star station",
                                tint = if (starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                addTarget?.let { s ->
                    val ready = playlistsState as? PlaylistsState.Ready
                    val playlistName = addPlaylist
                    if (playlistName == null) {
                        GroupPickerSheet(
                            title = "Add to playlist",
                            options = ready?.playlists?.map { it.name } ?: emptyList(),
                            onPick = { addPlaylist = it },
                            onDismiss = { addTarget = null },
                        )
                    } else {
                        GroupPickerSheet(
                            title = "Add to group in $playlistName",
                            options = ready?.playlists?.firstOrNull { it.name == playlistName }
                                ?.groups?.map { it.title } ?: emptyList(),
                            onPick = { group ->
                                graph.opsRepository.addTargeted(s, playlistName, group)
                                addTarget = null
                            },
                            onDismiss = { addTarget = null },
                            newOptionLabel = "New group",
                        )
                    }
                }
            }
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
