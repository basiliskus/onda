package dev.basiliskus.onda.sync

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class FolderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("onda", Context.MODE_PRIVATE)
    var treeUri: Uri?
        get() = prefs.getString("tree_uri", null)?.let(Uri::parse)
        set(value) = prefs.edit { putString("tree_uri", value?.toString()) }
    val lastPlayedMediaId: String? get() = prefs.getString("last_played_media_id", null)

    /** Resolves resumption when a move has invalidated [lastPlayedMediaId]. */
    val lastPlayedUrl: String? get() = prefs.getString("last_played_url", null)

    /**
     * The only writer, because the two values must describe the same station: the url is
     * consulted precisely when the id stops resolving, so a mismatched pair resumes some
     * other station with no error. Read-only properties plus one setter make that
     * unskippable rather than conventional, and collapse two commits into one.
     */
    fun setLastPlayed(id: String?, url: String?) = prefs.edit {
        putString("last_played_media_id", id)
        putString("last_played_url", url)
    }
}
