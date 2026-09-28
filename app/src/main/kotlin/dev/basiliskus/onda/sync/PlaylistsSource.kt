package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.model.PlaylistsState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** What the synced-folder writers consume of PlaylistRepository — the seam that keeps
 *  SAF/DocumentFile behind a fakeable boundary for JVM tests. A consumer-defined
 *  interface, so it lives beside its consumers (OpsRepository, SongCaptureRepository),
 *  not inside the class that happens to implement it (GitHub #28). */
interface PlaylistsSource {
    val state: StateFlow<PlaylistsState>
    val folderTicks: SharedFlow<Unit>
    /** Blocking I/O — call from an IO dispatcher. */
    fun readSyncedText(name: String): Result<String?>
    /** Blocking I/O — call from an IO dispatcher. */
    fun writeSyncedText(name: String, text: String): Boolean
    /**
     * Blocking I/O — call from an IO dispatcher. Appends [line] — one record of one or more
     * complete, already-neutralized lines (TitlesLog joins a multi-line record with the `\n`
     * the log frames records with) — plus a trailing newline to recordings/onda-titles.log
     * inside the synced folder, creating the subdirectory and the file if needed. Takes no
     * filename: onda-titles.log is the only file Onda may append to (pyradio owns its own
     * log; one writer per file), so appending anywhere else is not a convention to respect
     * but unrepresentable in this API.
     *
     * Returns false — never true — when the log cannot be reached under exactly that name, so
     * the caller reports a failed like instead of the log quietly sharding across
     * provider-mangled filenames the desktop harvester never globs.
     */
    fun appendTitlesLogLine(line: String): Boolean
}
