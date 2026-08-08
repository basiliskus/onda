package dev.basiliskus.onda.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import kotlin.test.*

/** The guard, the destination, and the outcome mapping around :core's line formatter (the
 *  format itself is canon in TitlesLogTest). SAF append, the Media3 custom action, the Toast
 *  and the sheet are not reachable from the JVM harness — they live in the manual matrix. */
@OptIn(ExperimentalCoroutinesApi::class)
class SongCaptureRepositoryTest {
    /** Hard-coded, not derived from TitlesLog — a tautology would prove nothing. */
    private val expectedLine = "Jul 23 (Thu) 21:08 |     Artist - Title (LIKED)"
    private val pyradioLine = "Jul 23 (Thu) 20:55 |     Someone - Else (LIKED)"

    private fun capture(
        fake: FakePlaylistsSource,
        scheduler: TestCoroutineScheduler,
        scope: CoroutineScope = CoroutineScope(StandardTestDispatcher(scheduler)),
    ) = SongCaptureRepository(fake, scope, clock = { LocalDateTime.of(2026, 7, 23, 21, 8) },
        ioDispatcher = StandardTestDispatcher(scheduler))

    @Test fun `capture appends one liked line to the titles log`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.Saved, r.capture("Artist - Title", station = null))
        assertEquals(expectedLine + "\n", fake.files[TITLES_LOG_PATH])
    }

    /** The seam takes no filename and the fake keys its log on a literal, so this equality is
     *  the only thing coupling the harness to the name production actually uses — renaming
     *  either constant fails here instead of silently moving the file the desktop harvester
     *  globs. It pins the name only; that a SAF append really lands at that path in the synced
     *  folder is observable on a device alone (smoke checklist row 17). */
    @Test fun `production writes the titles log at the path the fake mirrors`() {
        assertEquals(
            TITLES_LOG_PATH,
            "${PlaylistRepository.RECORDINGS_DIR}/${PlaylistRepository.TITLES_LOG}",
        )
    }

    @Test fun `capture touches no file other than the titles log`() = runTest {
        val fake = FakePlaylistsSource()
        fake.files[JOURNAL] = "{\"op\":\"add\"}\n"
        fake.files["recordings/pyradio-titles.log"] = pyradioLine + "\n"
        val before = fake.files.toMap()
        val r = capture(fake, testScheduler)

        r.capture("Artist - Title", station = null)
        assertEquals(before[JOURNAL], fake.files[JOURNAL])
        assertEquals(before["recordings/pyradio-titles.log"], fake.files["recordings/pyradio-titles.log"])
        assertEquals(before.keys + TITLES_LOG_PATH, fake.files.keys)
        assertEquals(0, fake.writes, "capture must never use the whole-file rewrite path")
    }

    @Test fun `append preserves existing content`() = runTest {
        val fake = FakePlaylistsSource()
        val seed = pyradioLine + "\n" + "Jul 23 (Thu) 20:56 | Station: KEXP\n"
        fake.files[TITLES_LOG_PATH] = seed
        val r = capture(fake, testScheduler)

        r.capture("Artist - Title", station = null)
        val after = fake.files.getValue(TITLES_LOG_PATH)
        assertTrue(after.startsWith(seed), "existing content was not preserved verbatim")
        assertEquals(3, fake.titlesLogLines().size)
        assertEquals(expectedLine, fake.titlesLogLines().last())
    }

    @Test fun `successive captures append in call order`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        // Ordering here is supplied by the FIFO StandardTestDispatcher, which resumes the two
        // launches in submission order. This is an ordering-regression row only: it is NOT
        // evidence that the Mutex prevents interleaving on the multi-threaded production
        // Dispatchers.IO, which the harness structurally cannot exercise.
        launch { r.capture("First - Song", station = null) }
        launch { r.capture("Second - Song", station = null) }
        advanceUntilIdle()
        assertEquals(
            listOf("Jul 23 (Thu) 21:08 |     First - Song (LIKED)",
                   "Jul 23 (Thu) 21:08 |     Second - Song (LIKED)"),
            fake.titlesLogLines(),
        )
    }

    @Test fun `null stream title writes nothing`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.NoTitle, r.capture(null, station = null))
        assertEquals(0, fake.appends)
        assertTrue(fake.files.isEmpty())
    }

    @Test fun `blank stream title writes nothing`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.NoTitle, r.capture("   ", station = null))
        assertEquals(0, fake.appends)
        assertTrue(fake.files.isEmpty())
    }

    /** The fake's append is atomic, so an unchanged file here proves the repository issued no
     *  further write — NOT that a real SAF append is all-or-nothing. It is not (plan R6); a
     *  truncated last line is covered by appendTitlesLogLine's partial-write note instead. */
    @Test fun `append failure reports WriteFailed and the repository writes nothing more`() = runTest {
        val fake = FakePlaylistsSource()
        fake.files[TITLES_LOG_PATH] = pyradioLine + "\n"
        val r = capture(fake, testScheduler)

        fake.failAppends = true
        assertEquals(SongCaptureRepository.Outcome.WriteFailed, r.capture("Artist - Title", station = null))
        assertEquals(pyradioLine + "\n", fake.files[TITLES_LOG_PATH])
        assertEquals(1, fake.appends, "the failure must be an observed attempt, not a skipped one")
    }

    /** PlaylistRepository.titlesLogFile refuses a provider-mangled name instead of appending
     *  under it, because the exact-name lookup would keep missing and every later like would
     *  create a fresh uniquely-named file — one line each, all reported as "Liked". This row
     *  holds the caller's half of that contract: a resolve failure surfaces WriteFailed and
     *  leaves nothing behind. The SAF ladder that produces the fault is device-only. */
    @Test fun `an unresolvable titles log fails the like instead of sharding silently`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        fake.unresolvableTitlesLog = true
        assertEquals(SongCaptureRepository.Outcome.WriteFailed, r.capture("Artist - Title", station = null))
        assertTrue(fake.files.isEmpty(), "a refused resolve left a shard: ${fake.files.keys}")
        assertEquals(1, fake.appends, "the failure must be an observed attempt, not a skipped one")
    }

    @Test fun `append failure after a successful capture leaves the earlier line intact`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.Saved, r.capture("Artist - Title", station = null))
        fake.failAppends = true
        assertEquals(SongCaptureRepository.Outcome.WriteFailed, r.capture("Second - Song", station = null))
        assertEquals(listOf(expectedLine), fake.titlesLogLines())
    }

    @Test fun `untrimmed title is written raw`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.Saved, r.capture("  A - B  ", station = null))
        assertEquals("Jul 23 (Thu) 21:08 |       A - B   (LIKED)", fake.titlesLogLines().single())
    }

    @Test fun `captured line matches the pinned format literally`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        r.capture("Artist - Title", station = null)
        assertEquals(expectedLine, fake.titlesLogLines().single())
    }

    /** like() is the sheet's entry point and reports only through [SongCaptureRepository.messages].
     *  Collected UNDISPATCHED because the flow has no replay — a collector started later would
     *  miss the emission and the row would pass vacuously. */
    @Test fun `like reports the outcome that actually occurred`() = runTest {
        suspend fun messageFor(title: String?, failAppends: Boolean): String {
            val fake = FakePlaylistsSource().apply { this.failAppends = failAppends }
            val r = capture(fake, testScheduler)
            val seen = mutableListOf<String>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { r.messages.collect { seen += it } }
            r.like(title, station = null)
            advanceUntilIdle()
            job.cancel()
            return seen.single()
        }

        assertEquals(captureMessage(SongCaptureRepository.Outcome.Saved), messageFor("Artist - Title", false))
        assertEquals(captureMessage(SongCaptureRepository.Outcome.NoTitle), messageFor("   ", false))
        assertEquals(captureMessage(SongCaptureRepository.Outcome.WriteFailed), messageFor("Artist - Title", true))
    }

    /**
     * The sheet closes the moment Like is tapped, so the capture must not live on the caller's
     * scope. What this row can prove is ownership: cancelling the injected scope before the
     * work runs must cancel the capture, which holds only if like() launched there. A stray
     * GlobalScope/caller-scope implementation would write anyway and fail here.
     *
     * It deliberately does NOT claim the real-world property — "the write still lands when the
     * sheet closes mid-append". Every row here runs on one FIFO StandardTestDispatcher, so the
     * caller cannot be cancelled between like() and the append; asserting that would pass
     * whatever the scope. Ownership is the proxy the harness can actually observe; the live
     * behaviour is smoke-checklist territory.
     */
    @Test fun `like launches on the injected scope, not the caller's`() = runTest {
        // Positive half first, and it is load-bearing: the negative half alone asserts absence,
        // which also holds if like() ran somewhere the test scheduler never drives (GlobalScope) —
        // it would pass for the wrong reason. Together they pin the scope from both sides.
        val live = FakePlaylistsSource()
        val liveScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        capture(live, testScheduler, scope = liveScope).like("Artist - Title", station = null)
        advanceUntilIdle()
        assertEquals(listOf(expectedLine), live.titlesLogLines(), "like() did not run on the injected scope")
        liveScope.cancel()

        val cancelled = FakePlaylistsSource()
        val deadScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        capture(cancelled, testScheduler, scope = deadScope).like("Artist - Title", station = null)
        deadScope.cancel()
        advanceUntilIdle()
        assertEquals(emptyList(), cancelled.titlesLogLines(), "capture outlived the scope it was launched on")
    }

    /** Guards the disjoint feedback channels: the notification path calls capture() and toasts
     *  the returned Outcome itself, so an emission here would double-report one tap.
     *  Mutation: making capture() emit fails this row. */
    @Test fun `capture does not emit — only like reports through messages`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)
        val seen = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { r.messages.collect { seen += it } }

        assertEquals(SongCaptureRepository.Outcome.Saved, r.capture("Artist - Title", station = null))
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(expectedLine), fake.titlesLogLines(), "the write must still land")
        assertTrue(seen.isEmpty(), "capture() reported through messages: $seen")
    }

    /** The pair's bytes are canon in TitlesLogTest; these rows pin what the REPOSITORY adds:
     *  the record reaches the seam as one append, whole or not at all. */
    @Test fun `capture with a station appends the pair as one record`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(
            SongCaptureRepository.Outcome.Saved,
            r.capture("Artist - Title", "KEXP 90.3 Seattle  WA (AAC 160K)"),
        )
        assertEquals(1, fake.appends, "the pair must reach the seam as a single append")
        assertEquals(
            listOf("Jul 23 (Thu) 21:08 | >>> Station: KEXP 90.3 Seattle  WA (AAC 160K)", expectedLine),
            fake.titlesLogLines(),
        )
    }

    @Test fun `a blank station writes the liked line alone`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.Saved, r.capture("Artist - Title", "   "))
        assertEquals(listOf(expectedLine), fake.titlesLogLines())
    }

    @Test fun `a failed append with a station tears neither line into the log`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        fake.failAppends = true
        assertEquals(
            SongCaptureRepository.Outcome.WriteFailed,
            r.capture("Artist - Title", "KEXP"),
        )
        assertEquals(1, fake.appends, "the failure must be an observed attempt, not a skipped one")
        assertTrue(fake.files.isEmpty(), "a torn pair reached the log: ${fake.files}")
    }

    @Test fun `a station line never appears without a liked line`() = runTest {
        // The NoTitle guard runs before the record is built, so a station with no likeable
        // title produces no write at all — not a dangling attribution line.
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.NoTitle, r.capture("   ", "KEXP"))
        assertEquals(0, fake.appends)
        assertTrue(fake.files.isEmpty())
    }

    @Test fun `each outcome maps to its own user-visible message`() {
        val messages = SongCaptureRepository.Outcome.entries.map(::captureMessage)
        assertEquals(messages.size, messages.toSet().size, "outcomes share a message: $messages")
        assertTrue(messages.all { it.isNotBlank() })
        messages.forEach { m ->
            assertFalse(m.contains("/"), "message leaks a path: $m")
            assertFalse(m.contains("content://"), "message leaks a tree uri: $m")
            assertFalse(m.contains(".log"), "message leaks a filename: $m")
        }
    }
}
