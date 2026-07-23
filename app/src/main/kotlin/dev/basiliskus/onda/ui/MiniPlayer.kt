package dev.basiliskus.onda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.basiliskus.onda.playback.NowPlaying

@Composable
fun MiniPlayer(np: NowPlaying, onToggle: () -> Unit, onExpand: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StationLogo(np.stationName, np.artworkUrl, size = 40)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(np.stationName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = np.error ?: if (np.isBuffering) "Buffering…" else np.streamTitle
                sub?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (np.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggle) {
                Icon(if (np.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (np.isPlaying) "Stop" else "Play")
            }
        }
    }
}
