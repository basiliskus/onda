package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.model.PlaylistsState
import dev.basiliskus.onda.core.ops.Op
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import kotlin.test.*

/**
 * The undo-integrity cluster (v0.5.2 P1 — GitHub #25, #24, #30): the token and feedback
 * subsystem made honest under races. Each row here is the deterministic observation that
 * separates the broken path from the fixed one — written at Diagnosis, failing against the
 * pre-fix code, and kept as the regression suite. #24's rows were removed together with the
 * pending.csv migration they pinned (GitHub #27).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UndoIntegrityTest {

    /**
     * #25 — an undo tapped before the mutate coroutine runs must suppress the mutation, not
     * silently no-op. The discriminating observation is the WRITE COUNT: the pre-fix path
     * lets the mutation reach the journal and (at best) restores it afterwards — two writes,
     * and a window in which the desktop can consume the mutation (the 2026-08-07 field
     * sighting). The fixed path writes nothing at all.
     */
    @Test fun `an undo tapped before the mutate runs suppresses the mutation entirely`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()
        val errors = collectErrors(r)
        val writesBefore = fake.writes

        val token = r.delete(s)
        r.undo(token) // the tap lands before the mutate coroutine has run
        advanceUntilIdle()

        assertEquals(
            writesBefore, fake.writes,
            "the undone mutation must never reach the journal (a write here is the desktop-consumption window)",
        )
        assertEquals(emptyList(), fake.journalOps())
        val st = r.effectivePlaylists.value
        assertTrue(st is PlaylistsState.Ready && st.playlists.flatMap { it.stations }.any { it.url == "http://u" },
            "the station must still be present — the delete was undone")
        assertEquals(emptyList(), errors, "a suppressed mutation IS its undo — nothing to report (#18)")
    }

    /** The late undo keeps its exact pre-fix semantics: mutation lands, snapshot restores. */
    @Test fun `an undo after the mutate restores the snapshot as before`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()
        val errors = collectErrors(r)

        val token = r.delete(s)
        advanceUntilIdle() // mutate has run; journal holds the delete
        assertEquals(1, fake.journalOps().size)
        r.undo(token)
        advanceUntilIdle()

        assertEquals(emptyList(), fake.journalOps())
        assertEquals(emptyList(), errors, "a successful undo has nothing to report (#18)")
    }

    /**
     * #18 — a voided undo must say so: the tap did nothing, and before this row the user
     * got no signal (field shape: a refresh inside the snackbar window, or the older of
     * two rapid deletes). The suppression and success rows above pin the message fires
     * ONLY here.
     */
    @Test fun `a voided undo reports Nothing to undo instead of silently no-oping`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()
        val errors = collectErrors(r)

        val token = r.delete(s)
        advanceUntilIdle() // armed; journal = [delete]
        // A GENUINE journal change (the desktop appended an op) — a content-identical
        // refresh no longer voids; see the row below.
        fake.seedJournal(*(fake.journalOps() + addOp("http://z")).toTypedArray())
        r.refresh()
        advanceUntilIdle()
        r.undo(token)
        advanceUntilIdle()

        assertEquals(listOf(UNDO_VOIDED_MESSAGE), errors)
        assertEquals(
            1, fake.journalOps().filterIsInstance<Op.Delete>().size,
            "the voided undo must not restore anything — the delete stands",
        )
    }

    /**
     * PR #66 review — every foreground resume ticks the folder watcher even when nothing
     * changed, and an unconditional setOps voided live tokens on that no-op refresh: with
     * #18's feedback the tap then reported "Nothing to undo" about a delete that still
     * stood. A content-identical re-read must leave the token live.
     */
    @Test fun `a content-identical refresh does not void a live undo token`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()
        val errors = collectErrors(r)

        val token = r.delete(s)
        advanceUntilIdle()
        r.refresh() // the same journal bytes re-read — the no-op foreground-tick shape
        advanceUntilIdle()
        r.undo(token)
        advanceUntilIdle()

        assertEquals(emptyList(), fake.journalOps(), "the undo restored — the token survived the no-op refresh")
        assertEquals(emptyList(), errors)
    }

    /**
     * #30 — a message emitted with no collector must survive until one arrives: the app
     * scope outlives the Activity, so a WriteFailed emitted during recreation was dropped
     * by the replay-less SharedFlow and the failed like reported nothing.
     */
    @Test fun `a capture message emitted with no collector reaches a late collector`() = runTest {
        val fake = FakePlaylistsSource()
        val r = SongCaptureRepository(
            fake, CoroutineScope(StandardTestDispatcher(testScheduler)),
            clock = { LocalDateTime.of(2026, 8, 8, 12, 0) },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        fake.failAppends = true
        r.like("Artist - Title", null)
        advanceUntilIdle() // WriteFailed emitted; nobody is collecting

        val seen = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { r.messages.collect { seen += it } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(
            listOf(captureMessage(SongCaptureRepository.Outcome.WriteFailed)), seen,
            "the dropped WriteFailed is the one message that matters",
        )
    }

    /** #30, the errors half — same contract for OpsRepository.errors. */
    @Test fun `an ops error emitted with no collector reaches a late collector`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()

        fake.failWrites = true
        r.delete(s)
        advanceUntilIdle() // rollback emitted UNAVAILABLE_MESSAGE; nobody collecting

        val seen = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { r.errors.collect { seen += it } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(UNAVAILABLE_MESSAGE), seen)
    }

    /**
     * PR #65 round 3 — distinct messages in one collector-absence window must BOTH
     * survive: capacity 1 evicted the older one, so a later success could mask an unseen
     * WriteFailed and a fresh error could evict a corruption report.
     */
    @Test fun `two errors emitted with no collector both reach a late collector`() = runTest {
        val a = station("http://a")
        val b = station("http://b")
        val fake = FakePlaylistsSource(ready(a, b))
        val r = repo(fake)
        advanceUntilIdle()

        fake.failWrites = true
        r.delete(a)
        advanceUntilIdle() // rollback emitted UNAVAILABLE_MESSAGE; nobody collecting
        r.delete(b)
        advanceUntilIdle() // second UNAVAILABLE_MESSAGE — must not evict the first

        val seen = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { r.errors.collect { seen += it } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(UNAVAILABLE_MESSAGE, UNAVAILABLE_MESSAGE), seen)
    }
}
