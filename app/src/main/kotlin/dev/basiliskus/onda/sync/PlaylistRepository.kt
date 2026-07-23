package dev.basiliskus.onda.sync

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.basiliskus.onda.core.m3u.M3uParser
import dev.basiliskus.onda.core.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistsState {
    data object NeedsFolder : PlaylistsState
    data object Loading : PlaylistsState
    data class Ready(val playlists: List<Playlist>) : PlaylistsState
}

class PlaylistRepository(
    private val context: Context,
    private val prefs: FolderPrefs,
    private val scope: CoroutineScope,
) {
    private val knownOrder = listOf("stations", "nts", "fip", "bbc", "public-radio", "radio-garden")
    private val _state = MutableStateFlow<PlaylistsState>(
        if (prefs.treeUri == null) PlaylistsState.NeedsFolder else PlaylistsState.Loading
    )
    val state: StateFlow<PlaylistsState> = _state
    private var lastHash = 0

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = rescan()
        })
        registerObserver()
    }

    fun setFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.treeUri = uri
        registerObserver()
        rescan()
    }

    fun rescan() {
        val tree = prefs.treeUri ?: run { _state.value = PlaylistsState.NeedsFolder; return }
        scope.launch(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(context, tree)
            if (dir == null || !dir.canRead()) {
                _state.value = PlaylistsState.NeedsFolder; return@launch
            }
            val texts = dir.listFiles()
                .filter { it.name?.endsWith(".m3u") == true }
                .mapNotNull { f ->
                    val name = f.name!!.removeSuffix(".m3u")
                    context.contentResolver.openInputStream(f.uri)?.use { ins ->
                        name to ins.reader().readText()
                    }
                }
            val hash = texts.hashCode()
            if (hash == lastHash && _state.value is PlaylistsState.Ready) return@launch
            lastHash = hash
            val playlists = texts.map { (name, text) -> M3uParser.parsePlaylist(name, text) }
                .sortedWith(compareBy({ knownOrder.indexOf(it.name).let { i -> if (i == -1) knownOrder.size else i } }, { it.name }))
            _state.value = PlaylistsState.Ready(playlists)
        }
    }

    fun readPendingText(): String? {
        val tree = prefs.treeUri ?: return null
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return null
        val file = dir.findFile("pending.csv") ?: return null
        return context.contentResolver.openInputStream(file.uri)?.use { it.reader().readText() }
    }

    fun writePendingText(text: String) {
        val tree = prefs.treeUri ?: return
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return
        val tmp = dir.findFile("pending.csv.tmp")?.also { it.delete() }
            .let { dir.createFile("text/csv", "pending.csv.tmp")!! }
        context.contentResolver.openOutputStream(tmp.uri, "wt")!!.use { it.write(text.toByteArray()) }
        dir.findFile("pending.csv")?.delete()
        DocumentsContract.renameDocument(context.contentResolver, tmp.uri, "pending.csv")
    }

    private fun registerObserver() {
        val tree = prefs.treeUri ?: return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree)
        )
        context.contentResolver.registerContentObserver(childrenUri, true,
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) = rescan()
            })
    }
}
