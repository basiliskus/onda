package dev.basiliskus.onda.sync

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class FolderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("onda", Context.MODE_PRIVATE)
    var treeUri: Uri?
        get() = prefs.getString("tree_uri", null)?.let(Uri::parse)
        set(value) = prefs.edit { putString("tree_uri", value?.toString()) }
}
