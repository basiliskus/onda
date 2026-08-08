package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.capture.TitlesLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Captures the currently playing track into the synced titles log the desktop harvester
 * ingests. Unlike ops.jsonl there is no in-memory mirror and no optimistic overlay: a like is
 * an append that either lands or is reported back to the caller.
 */
internal class SongCaptureRepository(
    private val playlists: PlaylistsSource,
    private val scope: CoroutineScope,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Media3's SessionResult codes are mapped in PlaybackService, which already owns the
     *  UnstableApi opt-in those constants need — this layer stays free of media3. */
    enum class Outcome { Saved, NoTitle, WriteFailed }

    private val mutex = Mutex()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Outcomes of [like] only. [capture] stays silent here so the sheet's snackbar and the
     *  notification's toast never both fire for one tap — see [captureMessage]. */
    val messages: SharedFlow<String> = _messages

    /**
     * Appends the liked record for [streamTitle] — a `>>> Station:` line plus the liked line
     * when [station] names one, the liked line alone otherwise — or reports why it could not.
     * The record reaches the seam as ONE append, so the pair cannot be torn by a failure or
     * process death between two writes; a station line is never written without its liked
     * line. Serialisation is the [mutex]'s job on the production multi-threaded
     * Dispatchers.IO; the tests run on a FIFO StandardTestDispatcher and structurally cannot
     * exercise an interleaving.
     *
     * The blank guard uses takeIf, which hands back the original instance — a title is never
     * trimmed on the way out, because the desktop consumer strips it itself.
     */
    suspend fun capture(streamTitle: String?, station: String?): Outcome = withContext(ioDispatcher) {
        val title = streamTitle?.takeIf { it.isNotBlank() } ?: return@withContext Outcome.NoTitle
        mutex.withLock {
            if (playlists.appendTitlesLogLine(TitlesLog.likedRecord(title, station, clock()))) Outcome.Saved
            else Outcome.WriteFailed
        }
    }

    /**
     * Fire-and-forget capture for UI callers, reporting through [messages].
     *
     * Runs on the app-wide [scope], not the caller's: a like tapped in the Now Playing sheet
     * must still land if the sheet closes or the activity is recreated before the append
     * completes. A capture that writes nothing is the failure this feature must not have.
     *
     * The write is guaranteed; the message is best-effort. [messages] is replay-less with a
     * one-slot buffer (matching OpsRepository.errors — diverging for this one caller would be
     * worse than the gap), so a message is dropped if nothing is collecting (activity
     * recreating, app backgrounded) or if a second like arrives while the first snackbar is
     * still showing. Reaching the latter from the sheet needs a reopen between taps. A dropped
     * WriteFailed is the case that would matter; Channel(BUFFERED).receiveAsFlow() is the
     * primitive that would close it, for both repositories at once.
     */
    fun like(streamTitle: String?, station: String?) {
        scope.launch { _messages.tryEmit(captureMessage(capture(streamTitle, station))) }
    }
}

/** One outcome, exactly one message — shared by the sheet's snackbar and the notification's
 *  toast so a capture never reports itself twice or differently. Names no path or URI. */
internal fun captureMessage(outcome: SongCaptureRepository.Outcome): String = when (outcome) {
    SongCaptureRepository.Outcome.Saved -> "Liked"
    SongCaptureRepository.Outcome.NoTitle -> "No track info to like"
    SongCaptureRepository.Outcome.WriteFailed -> "Couldn't save the like — synced folder unreachable"
}
