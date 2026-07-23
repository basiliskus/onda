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

    /**
     * Blocking I/O — call from Dispatchers.IO.
     * success(text) when pending.csv was read; success(null) when it legitimately
     * doesn't exist yet (nothing pending); failure(e) when the synced folder tree is
     * unreachable or the read otherwise failed.
     */
    fun readPendingText(): Result<String?> {
        val tree = prefs.treeUri ?: return Result.failure(IOException("no folder configured"))
        return try {
            val dir = DocumentFile.fromTreeUri(context, tree)
                ?: return Result.failure(IOException("synced folder tree unreachable"))
            val file = dir.findFile("pending.csv") ?: return Result.success(null)
            val text = context.contentResolver.openInputStream(file.uri)?.use { it.reader().readText() }
                ?: return Result.failure(IOException("openInputStream returned null for pending.csv"))
            Result.success(text)
        } catch (e: Exception) {
            Log.w("Onda", "readPendingText: failed", e)
            Result.failure(e)
        }
    }

    /**
     * Blocking I/O — call from Dispatchers.IO. Crash-safe: pending.csv is never deleted
     * without a verified replacement already renamed into place. Returns false (leaving
     * any existing pending.csv untouched) on any failure; true once the new content is
     * durably in place as pending.csv.
     */
    fun writePendingText(text: String): Boolean {
        val tree = prefs.treeUri ?: return false
        return try {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: return false

            // Clean up tmp/bak artifacts left behind by a previous crashed or interrupted write.
            dir.listFiles().forEach { f ->
                val name = f.name ?: return@forEach
                if (name.startsWith("pending.") && (name.contains(".tmp") || name.contains(".bak"))) {
                    f.delete()
                }
            }

            // Write the new content to a fresh tmp file, keeping the DocumentFile handle the
            // provider returned — re-finding it by name afterwards is unreliable (providers
            // mangle extensions).
            val tmp = dir.createFile("text/csv", "pending.csv.tmp") ?: run {
                Log.w("Onda", "writePendingText: createFile returned null"); return false
            }
            val out = context.contentResolver.openOutputStream(tmp.uri, "wt") ?: run {
                Log.w("Onda", "writePendingText: openOutputStream returned null")
                tmp.delete()
                return false
            }
            out.use { it.write(text.toByteArray()) }

            // Move any existing pending.csv aside before touching it. If that move fails and
            // pending.csv existed, bail without ever having deleted it.
            val existing = dir.findFile("pending.csv")
            var bakUri: Uri? = null
            if (existing != null) {
                bakUri = try {
                    DocumentsContract.renameDocument(context.contentResolver, existing.uri, "pending.csv.bak")
                } catch (e: Exception) {
                    Log.w("Onda", "writePendingText: rename to .bak failed", e); null
                }
                if (bakUri == null) {
                    tmp.delete()
                    return false
                }
            }

            // Promote tmp -> pending.csv; roll back the .bak on failure so a later retry still
            // has the old content to fall back to.
            val finalUri = try {
                DocumentsContract.renameDocument(context.contentResolver, tmp.uri, "pending.csv")
            } catch (e: Exception) {
                Log.w("Onda", "writePendingText: rename tmp to pending.csv failed", e); null
            }
            if (finalUri == null) {
                if (bakUri != null) {
                    try {
                        DocumentsContract.renameDocument(context.contentResolver, bakUri, "pending.csv")
                    } catch (e: Exception) {
                        Log.w("Onda", "writePendingText: rollback of .bak failed", e)
                    }
                }
                return false
            }

            if (bakUri != null) DocumentFile.fromSingleUri(context, bakUri)?.delete()
            true
        } catch (e: Exception) {
            Log.w("Onda", "writePendingText: failed", e)
            false
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
