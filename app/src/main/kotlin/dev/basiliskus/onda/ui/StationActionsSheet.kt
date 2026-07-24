package dev.basiliskus.onda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Long-press actions for a playlist station row. Remove duplicates the swipe
 * gesture on purpose — it is the TalkBack-reachable path to deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationActionsSheet(
    stationName: String,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stationName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onMove),
            leadingContent = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
            headlineContent = { Text("Move to group…") },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onRemove),
            leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            headlineContent = { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) },
        )
        Spacer(Modifier.height(24.dp))
    }
}
