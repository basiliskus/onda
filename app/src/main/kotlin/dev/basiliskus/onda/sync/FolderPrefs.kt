package dev.basiliskus.onda.sync

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class FolderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("onda", Context.MODE_PRIVATE)
    var treeUri: Uri?
        get() = prefs.getString("tree_uri", null)?.let(Uri::parse)
        set(value) = prefs.edit { putString("tree_uri", value?.toString()) }
    var lastPlayedMediaId: String?
        get() = prefs.getString("last_played_media_id", null)
        set(value) = prefs.edit { putString("last_played_media_id", value) }

    /** Written with [lastPlayedMediaId]; resolves resumption when a move has invalidated the id. */
    var lastPlayedUrl: String?
        get() = prefs.getString("last_played_url", null)
        set(value) = prefs.edit { putString("last_played_url", value) }
}
