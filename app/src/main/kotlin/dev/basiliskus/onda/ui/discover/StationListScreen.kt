package dev.basiliskus.onda.ui.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.basiliskus.onda.core.radiobrowser.RbStation
import dev.basiliskus.onda.ui.StationRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationListScreen(
    title: String,
    vm: DiscoverViewModel,
    playingUrl: String?,
    onBack: () -> Unit,
    onPlay: (RbStation) -> Unit,
    trailing: @Composable (RbStation) -> Unit = { s -> Text("▲ ${s.votes}") },
) {
    val state by vm.list.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }

    Column {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(state = listState) {
            itemsIndexed(state.stations, key = { i, s -> "$i:${s.stationuuid}" }) { _, s ->
                StationRow(
                    name = s.name,
                    subtitle = listOf(s.codec, "${s.bitrate}k", s.country).filter { it.isNotBlank() && it != "0k" }.joinToString(" · "),
                    logoUrl = s.favicon.ifBlank { null },
                    isPlaying = s.url == playingUrl,
                    onClick = { onPlay(s) },
                    trailing = { trailing(s) },
                )
            }
            if (state.loading) item { ListItem(headlineContent = { Text("Loading…") }) }
        }
    }
}
