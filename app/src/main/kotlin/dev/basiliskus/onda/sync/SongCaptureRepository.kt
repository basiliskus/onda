package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.capture.TitlesLog
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    private val _messages = Channel<String>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Outcomes of [like] only. [capture] stays silent here so the sheet's snackbar and the
     *  notification's toast never both fire for one tap — see [captureMessage]. Small
     *  newest-wins channel (4 slots, DROP_OLDEST), not a replay-less SharedFlow: a
     *  WriteFailed emitted while the Activity is recreating waits for the next collector
     *  instead of vanishing (GitHub #30). Four slots, not one: distinct outcomes in one
     *  burst must all survive — capacity 1 let a later like's "Liked" evict an unseen
     *  WriteFailed, reporting a failed capture as a success (PR #65 round 3) — while a
     *  relaunch still replays at most four stale outcomes, never a backlog. Single
     *  consumer by design (the snackbar host) — a prose contract the Flow type cannot
     *  enforce. See OpsRepository.errors for the windows this does NOT cover.
     *  enforced by: UndoIntegrityTest."a capture message emitted with no collector reaches a late collector" */
    val messages: Flow<String> = _messages.receiveAsFlow()

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
     * The write is guaranteed, and since GitHub #30 the message survives collector absence
     * too: [messages] is a small newest-wins channel (matching OpsRepository.errors), so an
     * outcome emitted while the Activity recreates waits for the next collector instead of
     * vanishing, and a burst of distinct outcomes survives up to four deep. Not covered: a
     * backgrounded app keeps its LaunchedEffect collector, so its snackbar times out
     * off-screen; delivery is at-most-once, so cancellation at the instant of handover can
     * still lose a message; and a burst deeper than the buffer evicts the oldest outcome.
     */
    fun like(streamTitle: String?, station: String?) {
        scope.launch { _messages.trySend(captureMessage(capture(streamTitle, station))) }
    }
}

/** One outcome, exactly one message — shared by the sheet's snackbar and the notification's
 *  toast so a capture never reports itself twice or differently. Names no path or URI. */
internal fun captureMessage(outcome: SongCaptureRepository.Outcome): String = when (outcome) {
    SongCaptureRepository.Outcome.Saved -> "Liked"
    SongCaptureRepository.Outcome.NoTitle -> "No track info to like"
    SongCaptureRepository.Outcome.WriteFailed -> "Couldn't save the like — synced folder unreachable"
}
