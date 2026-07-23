package dev.basiliskus.onda.sync

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.basiliskus.onda.core.m3u.M3uParser
import dev.basiliskus.onda.core.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

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
    private val scanMutex = Mutex()
    private var observer: ContentObserver? = null
    private val _folderTicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val folderTicks: SharedFlow<Unit> = _folderTicks

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                rescan()
                _folderTicks.tryEmit(Unit)
            }
        })
        registerObserver()
        // Covers service-only process starts (e.g. Android Auto/media browser connects
        // before any activity ever resumes) where ProcessLifecycleOwner.onStart never fires.
        if (prefs.treeUri != null) rescan()
    }

    fun setFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.treeUri = uri
        registerObserver()
        rescan()
        _folderTicks.tryEmit(Unit)
    }

    fun rescan() {
        val tree = prefs.treeUri ?: run { _state.value = PlaylistsState.NeedsFolder; return }
        scope.launch(Dispatchers.IO) {
            scanMutex.withLock {
                val files = try {
                    val dir = DocumentFile.fromTreeUri(context, tree)
                    if (dir == null || !dir.canRead()) {
                        _state.value = PlaylistsState.NeedsFolder; return@withLock
                    }
                    dir.listFiles().filter { it.name?.endsWith(".m3u") == true }
                } catch (e: Exception) {
                    // Tree itself is unreachable (SAF permission revoked, provider gone, etc.)
                    // — this is the only case that should knock us back to NeedsFolder.
                    Log.w("Onda", "rescan: tree unreachable", e)
                    _state.value = PlaylistsState.NeedsFolder
                    return@withLock
                }
                try {
                    val texts = files.mapNotNull { f ->
                        // A file mid-replace by sync (deleted/truncated/locked) shouldn't kill the
                        // whole scan — skip it; a skipped file changes the content hash naturally
                        // next scan, so it self-heals once sync settles.
                        try {
                            val name = f.name!!.removeSuffix(".m3u")
                            context.contentResolver.openInputStream(f.uri)?.use { ins ->
                                name to ins.reader().readText()
                            }
                        } catch (e: IOException) {
                            Log.w("Onda", "rescan: skipping unreadable file ${f.name}", e)
                            null
                        } catch (e: SecurityException) {
                            Log.w("Onda", "rescan: skipping unreadable file ${f.name}", e)
                            null
                        }
                    }
                    val hash = texts.hashCode()
                    if (hash == lastHash && _state.value is PlaylistsState.Ready) return@withLock
                    lastHash = hash
                    val playlists = texts.map { (name, text) -> M3uParser.parsePlaylist(name, text) }
                        .sortedWith(compareBy({ knownOrder.indexOf(it.name).let { i -> if (i == -1) knownOrder.size else i } }, { it.name }))
                    _state.value = PlaylistsState.Ready(playlists)
                } catch (e: Exception) {
                    // Unexpected failure past the tree-access check (e.g. a parse bug) — the
                    // tree itself is fine, so keep whatever last-good Ready state we had rather
                    // than bouncing the user back to the folder picker.
                    Log.w("Onda", "rescan: unexpected failure, keeping last-good state", e)
                }
            }
        }
    }

    /** Blocking I/O — call from Dispatchers.IO. Returns null on any failure. */
    fun readPendingText(): String? {
        val tree = prefs.treeUri ?: return null
        return try {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: return null
            val file = dir.findFile("pending.csv") ?: return null
            context.contentResolver.openInputStream(file.uri)?.use { it.reader().readText() }
        } catch (e: Exception) {
            Log.w("Onda", "readPendingText: failed", e); null
        }
    }

    /** Blocking I/O — call from Dispatchers.IO. Failures are logged, never thrown. */
    fun writePendingText(text: String) {
        val tree = prefs.treeUri ?: return
        try {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: return
            dir.findFile("pending.csv.tmp")?.delete()
            val tmp = dir.createFile("text/csv", "pending.csv.tmp")
            if (tmp == null) {
                Log.w("Onda", "writePendingText: createFile returned null"); return
            }
            val out = context.contentResolver.openOutputStream(tmp.uri, "wt")
            if (out == null) {
                Log.w("Onda", "writePendingText: openOutputStream returned null"); return
            }
            out.use { it.write(text.toByteArray()) }
            dir.findFile("pending.csv")?.delete()
            DocumentsContract.renameDocument(context.contentResolver, tmp.uri, "pending.csv")
        } catch (e: Exception) {
            Log.w("Onda", "writePendingText: failed", e)
        }
    }

    private fun registerObserver() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        val tree = prefs.treeUri ?: return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree)
        )
        val newObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                rescan()
                _folderTicks.tryEmit(Unit)
            }
        }
        observer = newObserver
        context.contentResolver.registerContentObserver(childrenUri, true, newObserver)
    }
}
