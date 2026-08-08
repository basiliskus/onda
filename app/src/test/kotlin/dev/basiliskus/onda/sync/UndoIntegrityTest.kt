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
 * pre-fix code, and kept as the regression suite.
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
    }

    /** The late undo keeps its exact pre-fix semantics: mutation lands, snapshot restores. */
    @Test fun `an undo after the mutate restores the snapshot as before`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.delete(s)
        advanceUntilIdle() // mutate has run; journal holds the delete
        assertEquals(1, fake.journalOps().size)
        r.undo(token)
        advanceUntilIdle()

        assertEquals(emptyList(), fake.journalOps())
    }

    /**
     * #24 — a migration is a journal change, so it must void live tokens even when the
     * refresh's own follow-up read fails before setOps can bump the generation. Pre-fix, the
     * stale token stays live and its undo overwrites the migrated journal — the pending.csv
     * rows are already deleted, so the migrated adds are lost permanently.
     */
    @Test fun `migration voids a live undo token even when the follow-up read fails`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.delete(s)
        advanceUntilIdle() // armed; journal = [delete]

        // pending.csv appears (synced from the desktop), and the refresh's own journal
        // re-read fails: reads are (1) LEGACY_CSV, (2) JOURNAL inside the migration,
        // then (3) JOURNAL again in refresh — fail exactly that third one.
        fake.files[LEGACY_CSV] =
            "# name,url,encoding,icon,profile,buffering,force-http,volume,referer,player,stationuuid,tags,country,votes\n" +
            "NTS,https://nts.example/stream,,,,,,,,,uuid-1,eclectic,United Kingdom,812\n"
        fake.readFailuresAt = setOf(fake.reads + 3)
        r.refresh()
        advanceUntilIdle() // migration wrote the merged journal and deleted the csv; bump owed

        r.undo(token)
        advanceUntilIdle()

        val urls = fake.journalOps().filterIsInstance<Op.Add>().map { it.station.url }
        assertTrue(
            "https://nts.example/stream" in urls,
            "undo after a migration clobbered the migrated add — the csv is already deleted, so it is lost permanently",
        )
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
}
