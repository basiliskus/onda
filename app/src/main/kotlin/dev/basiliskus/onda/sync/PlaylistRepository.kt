package dev.basiliskus.onda.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.basiliskus.onda.core.m3u.M3uParser
import dev.basiliskus.onda.core.model.PlaylistsState
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

class PlaylistRepository(
    private val context: Context,
    private val prefs: FolderPrefs,
    private val scope: CoroutineScope,
) : PlaylistsSource {
    private val knownOrder = listOf("stations", "nts", "fip", "bbc", "public-radio", "radio-garden")
    private val _state = MutableStateFlow<PlaylistsState>(
        if (prefs.treeUri == null) PlaylistsState.NeedsFolder else PlaylistsState.Loading
    )
    override val state: StateFlow<PlaylistsState> = _state
    private var lastHash = 0
    private val scanMutex = Mutex()
    private val _scanning = MutableStateFlow(false)
    /** True while a scan holds scanMutex — drives pull-to-refresh's spinner directly. */
    val scanning: StateFlow<Boolean> = _scanning
    // Content observers don't fire for direct writes on ExternalStorageProvider (probed
    // 2026-07-23) — Synology Drive's writes to the synced folder never trigger one. Hot
    // reload is instead: a rescan on every foreground resume, plus pull-to-refresh. Every
    // completed rescan emits a tick so journal readers re-read the folder too.
    private val _folderTicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val folderTicks: SharedFlow<Unit> = _folderTicks

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                rescan()
            }
        })
        // Covers service-only process starts (e.g. Android Auto/media browser connects
        // before any activity ever resumes) where ProcessLifecycleOwner.onStart never fires.
        if (prefs.treeUri != null) rescan()
    }

    fun setFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.treeUri = uri
        rescan()
    }

    fun rescan() {
        val tree = prefs.treeUri ?: run { _state.value = PlaylistsState.NeedsFolder; return }
        scope.launch(Dispatchers.IO) {
            scanMutex.withLock {
                _scanning.value = true
                try {
                    val files = try {
                        val dir = DocumentFile.fromTreeUri(context, tree)
                        if (dir == null || !dir.canRead()) {
                            onTreeUnreadable(tree, "dir null or unreadable"); return@withLock
                        }
                        dir.listFiles().filter { it.name?.endsWith(".m3u") == true }
                    } catch (e: Exception) {
                        onTreeUnreadable(tree, "threw", e)
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
                } finally {
                    _scanning.value = false
                }
            }
            // After the scan settles (even a failed or no-op one): journal readers only
            // read ops.jsonl on a tick, never trigger a scan, so no feedback loop.
            _folderTicks.tryEmit(Unit)
        }
    }

    /**
     * The tree failed to open or list just now. That alone doesn't mean the SAF grant is
     * gone — Synology Drive can make the provider transiently unavailable mid-sync. Only
     * bounce to NeedsFolder if we've actually lost the persisted permission; otherwise keep
     * whatever state we had and let the next rescan retry.
     */
    private fun onTreeUnreadable(tree: Uri, reason: String, e: Exception? = null) {
        val stillGranted = context.contentResolver.persistedUriPermissions.any { it.uri == tree }
        if (stillGranted) {
            Log.w("Onda", "rescan: tree transiently unreadable ($reason), keeping last state", e)
        } else {
            Log.w("Onda", "rescan: tree permission gone ($reason)", e)
            _state.value = PlaylistsState.NeedsFolder
        }
    }

    /**
     * Blocking I/O — call from Dispatchers.IO.
     * success(text) when [name] was read (recovering it from an orphaned .bak first, if
     * needed); success(null) when it legitimately doesn't exist yet; failure(e) when the
     * synced folder tree is unreachable or the read otherwise failed.
     */
    override fun readSyncedText(name: String): Result<String?> {
        val tree = prefs.treeUri ?: return Result.failure(IOException("no folder configured"))
        return try {
            val dir = DocumentFile.fromTreeUri(context, tree)
                ?: return Result.failure(IOException("synced folder tree unreachable"))
            recoverOrphanedBak(dir, name)
            val file = dir.findFile(name) ?: return Result.success(null)
            val text = context.contentResolver.openInputStream(file.uri)?.use { it.reader().readText() }
                ?: return Result.failure(IOException("openInputStream returned null for $name"))
            Result.success(text)
        } catch (e: Exception) {
            Log.w("Onda", "readSyncedText($name): failed", e)
            Result.failure(e)
        }
    }

    /**
     * Restores [name] from an orphaned [name].bak — the state left behind if the process
     * died after writeSyncedText renamed [name] -> .bak but before the new tmp was
     * promoted into its place (or before promote+rollback could complete). Matches the
     * exact name plus provider-mangled variants that still start with "[name].bak".
     * No-op if [name] already exists or no .bak is present.
     */
    private fun recoverOrphanedBak(dir: DocumentFile, name: String) {
        if (dir.findFile(name) != null) return
        val bak = dir.listFiles().firstOrNull { f -> f.name?.startsWith("$name.bak") == true }
            ?: return
        try {
            DocumentsContract.renameDocument(context.contentResolver, bak.uri, name)
            Log.w("Onda", "recoverOrphanedBak: restored orphaned $name.bak -> $name")
        } catch (e: Exception) {
            Log.w("Onda", "recoverOrphanedBak: failed to restore $name.bak", e)
        }
    }

    /**
     * Blocking I/O — call from Dispatchers.IO. Crash-safe choreography: at every step, at
     * least one of {[name], [name].bak} holds the last-good content, and readers and
     * writers restore from .bak before doing anything else (recoverOrphanedBak above).
     * [name] is never deleted without a verified replacement already renamed into place.
     * Returns false (leaving any existing [name] untouched) on any failure; true once the
     * new content is durably in place as [name].
     */
    override fun writeSyncedText(name: String, text: String): Boolean {
        val tree = prefs.treeUri ?: return false
        return try {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: return false

            recoverOrphanedBak(dir, name)

            // Clean up tmp/bak artifacts left behind by a previous crashed or interrupted
            // write. Only once a real target file exists — recoverOrphanedBak above already
            // restored it from .bak if that .bak held the last-good content.
            if (dir.findFile(name) != null) {
                dir.listFiles().forEach { f ->
                    val n = f.name ?: return@forEach
                    if (n.startsWith("$name.") && (n.contains(".tmp") || n.contains(".bak"))) {
                        f.delete()
                    }
                }
            }

            // Write the new content to a fresh tmp file, keeping the DocumentFile handle the
            // provider returned — re-finding it by name afterwards is unreliable (providers
            // mangle extensions).
            val tmp = dir.createFile(mimeFor(name), "$name.tmp") ?: run {
                Log.w("Onda", "writeSyncedText($name): createFile returned null"); return false
            }
            val out = context.contentResolver.openOutputStream(tmp.uri, "wt") ?: run {
                Log.w("Onda", "writeSyncedText($name): openOutputStream returned null")
                tmp.delete()
                return false
            }
            out.use { it.write(text.toByteArray()) }

            // Move any existing target file aside before touching it. If that move fails and
            // the file existed, bail without ever having deleted it.
            val existing = dir.findFile(name)
            var bakUri: Uri? = null
            if (existing != null) {
                bakUri = try {
                    DocumentsContract.renameDocument(context.contentResolver, existing.uri, "$name.bak")
                } catch (e: Exception) {
                    Log.w("Onda", "writeSyncedText($name): rename to .bak failed", e); null
                }
                if (bakUri == null) {
                    tmp.delete()
                    return false
                }
            }

            // Promote tmp -> name; roll back the .bak on failure so a later retry still has
            // the old content to fall back to.
            val finalUri = try {
                DocumentsContract.renameDocument(context.contentResolver, tmp.uri, name)
            } catch (e: Exception) {
                Log.w("Onda", "writeSyncedText($name): rename tmp to $name failed", e); null
            }
            if (finalUri == null) {
                if (bakUri != null) {
                    try {
                        DocumentsContract.renameDocument(context.contentResolver, bakUri, name)
                    } catch (e: Exception) {
                        Log.w("Onda", "writeSyncedText($name): rollback of .bak failed", e)
                    }
                }
                return false
            }

            if (bakUri != null) DocumentFile.fromSingleUri(context, bakUri)?.delete()
            true
        } catch (e: Exception) {
            Log.w("Onda", "writeSyncedText($name): failed", e)
            false
        }
    }

    /**
     * Blocking I/O — call from Dispatchers.IO. Deletes [name] plus any .tmp/.bak artifacts
     * the write choreography may have left for it. Returns true once [name] no longer
     * exists in the folder.
     */
    override fun deleteSyncedFile(name: String): Boolean {
        val tree = prefs.treeUri ?: return false
        return try {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: return false
            dir.listFiles().forEach { f ->
                val n = f.name ?: return@forEach
                if (n == name || (n.startsWith("$name.") && (n.contains(".tmp") || n.contains(".bak")))) {
                    f.delete()
                }
            }
            dir.findFile(name) == null
        } catch (e: Exception) {
            Log.w("Onda", "deleteSyncedFile($name): failed", e)
            false
        }
    }

    /**
     * Blocking I/O — call from Dispatchers.IO. Appends one record in a single write, in SAF
     * append mode ("wa" -> O_APPEND), rather than through the tmp/bak rewrite above: a
     * whole-file rewrite would race the desktop harvester (which reads and rotates this file
     * concurrently) and would rewrite an ever-growing log on every like, while a single
     * append of a short record only ever leaves whole lines visible to a concurrent reader.
     *
     * There is deliberately no read-modify-write fallback: a provider that ignored append
     * mode would silently truncate the log, which is strictly worse than returning false and
     * telling the user.
     *
     * Three accepted, documented-not-defended holes:
     * - **Rotation race.** If the harvester rotates the file between [titlesLogFile]'s lookup
     *   and the open below, this line follows the renamed inode into the archive. The next
     *   like re-resolves and lands in the fresh log — but the lost line is lost; nothing
     *   goes back to fetch it.
     * - **Partial write joined.** If a previous append left the last line unterminated (a
     *   crash mid-write), this append concatenates onto it. The joined line still contains
     *   `" | "` and still ends `" (LIKED)"`, so the desktop ingests it as one corrupted title
     *   rather than skipping it. Only a whole-record write could avoid this, and that is the
     *   rewrite race we chose append to escape.
     * - **Close-time failure.** `use` propagates an exception from `close()`, so a write that
     *   reached the file but failed to flush/close reports false. The direction is the safe
     *   one (never claim a like that did not land), but a user who re-taps then writes the
     *   line twice; the desktop collapses duplicate titles, so this is cosmetic.
     */
    override fun appendTitlesLogLine(line: String): Boolean {
        val tree = prefs.treeUri ?: return false
        return try {
            val root = DocumentFile.fromTreeUri(context, tree) ?: return false
            // Directory names are not mime-mangled, so this needs no rename dance.
            val dir = root.findFile(RECORDINGS_DIR)?.takeIf { it.isDirectory }
                ?: root.createDirectory(RECORDINGS_DIR) ?: return false
            val file = titlesLogFile(dir) ?: return false
            val out = context.contentResolver.openOutputStream(file.uri, "wa") ?: run {
                Log.w("Onda", "appendTitlesLogLine: openOutputStream returned null"); return false
            }
            out.use { it.write((line + "\n").toByteArray()) }
            true
        } catch (e: Exception) {
            Log.w("Onda", "appendTitlesLogLine: failed", e)
            false
        }
    }

    /**
     * The titles log, created on first use. AOSP keeps the name for the mime we pass:
     * ".log" is not in MimeTypeMap, so buildUniqueFile infers application/octet-stream, which
     * matches the requested mime and appends nothing. (The "onda-titles.log.txt" outcome is
     * what text/plain would have produced — the mime an earlier draft used.) The rename
     * de-mangling below stays as defence against third-party providers, Synology's included,
     * since the desktop harvester globs on the name.
     *
     * Resolution is by exact name only. A prefix scan would also match the desktop's rotation
     * backups ("onda-titles.log.1", ".bak"), and every later like would append invisibly into
     * an archive the harvester has already consumed. It also cannot pick up a Synology conflict
     * copy: those insert their marker before the extension
     * ("onda-titles_<host>_<date>_Conflict.log"). They should not exist for this file anyway —
     * it is written only by the phone and only read/rotated by the desktop, which is exactly
     * why the ROADMAP mandates a file separate from pyradio's.
     *
     * If the provider mangles the name and the rename cannot undo it, this deletes the stray
     * and returns null rather than handing back a mangled document. Appending to it would be
     * worse than failing: the harvester never reads it, and because the exact-name lookup would
     * keep missing, every later like would createFile a fresh uniquely-named file, write one
     * line and report "Liked" while the log shards. Returning null surfaces WriteFailed.
     */
    private fun titlesLogFile(dir: DocumentFile): DocumentFile? {
        dir.findFile(TITLES_LOG)?.takeIf { it.isFile }?.let { return it }
        val created = dir.createFile(mimeFor(TITLES_LOG), TITLES_LOG) ?: run {
            Log.w("Onda", "titlesLogFile: createFile returned null"); return null
        }
        if (created.name == TITLES_LOG) return created
        val renamed = try {
            DocumentsContract.renameDocument(context.contentResolver, created.uri, TITLES_LOG)
                ?.let { DocumentFile.fromSingleUri(context, it) }
        } catch (e: Exception) {
            Log.w("Onda", "titlesLogFile: rename to $TITLES_LOG failed", e)
            null
        }
        if (renamed?.name == TITLES_LOG) return renamed
        Log.w("Onda", "titlesLogFile: provider kept ${created.name}; refusing to shard the log")
        (renamed ?: created).delete()
        return null
    }

    // Mime influences how the provider names a created file. For the writeSyncedText tmp file
    // that is harmless — its handle is kept, so the name never has to be re-resolved. For the
    // titles log it is not: titlesLogFile deletes and refuses a mangled name rather than shard
    // the log, so this must stay single-sourced with the extension it is called for.
    // text/csv preserved for csv files as the E2E-proven choice.
    private fun mimeFor(name: String) =
        if (name.endsWith(".csv")) "text/csv" else "application/octet-stream"

    /** Internal, not private, so the JVM tests can pin the two names the desktop harvester
     *  globs on (SongCaptureRepositoryTest's golden-path row) — renaming either here must fail
     *  a test rather than silently move the file. Nothing outside this module reads them; the
     *  seam below still takes no filename. */
    internal companion object {
        const val RECORDINGS_DIR = "recordings"
        const val TITLES_LOG = "onda-titles.log"
    }
}
