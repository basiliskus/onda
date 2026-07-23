package dev.basiliskus.onda.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.basiliskus.onda.playback.NowPlaying

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(np: NowPlaying, onToggle: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StationLogo(np.stationName, np.artworkUrl, size = 160)
            Spacer(Modifier.height(24.dp))
            Text(np.stationName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            when {
                np.error != null -> Text(np.error, color = MaterialTheme.colorScheme.error)
                np.isBuffering -> Text("Buffering…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                np.streamTitle != null -> Text(np.streamTitle, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (np.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    if (np.isPlaying) "Stop" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}
