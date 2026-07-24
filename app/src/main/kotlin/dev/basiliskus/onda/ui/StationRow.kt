package dev.basiliskus.onda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import coil3.compose.SubcomposeAsyncImage
import dev.basiliskus.onda.ui.theme.accentFor

@Composable
fun StationRow(
    name: String,
    subtitle: String?,
    logoUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    pending: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { StationLogo(name, logoUrl) },
        headlineContent = {
            Text(
                name,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (pending) FontStyle.Italic else FontStyle.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = { subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = { trailing() },
    )
}

@Composable
fun StationLogo(name: String, logoUrl: String?, size: Int = 44) {
    val tile: @Composable () -> Unit = {
        Box(
            Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(accentFor(name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.surface,
            )
        }
    }
    if (logoUrl.isNullOrBlank()) tile() else SubcomposeAsyncImage(
        model = logoUrl,
        contentDescription = null,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
        loading = { tile() },
        error = { tile() },
    )
}
