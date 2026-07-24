package dev.basiliskus.onda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Bottom-sheet picker over a list of names (playlist names or a playlist's groups).
 * A non-null [newOptionLabel] adds a free-text field for minting a new entry — used
 * for "New group"; playlist pickers pass null since playlists are files the app
 * cannot create.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPickerSheet(
    title: String,
    options: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    newOptionLabel: String? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(options, key = { it }) { option ->
                ListItem(
                    modifier = Modifier.clickable { onPick(option) },
                    headlineContent = { Text(option) },
                )
            }
        }
        if (newOptionLabel != null) {
            var newName by remember { mutableStateOf("") }
            val submit = { newName.trim().takeIf { it.isNotEmpty() }?.let(onPick) }
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(newOptionLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { submit() }, enabled = newName.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = "Create")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
