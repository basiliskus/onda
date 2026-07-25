package dev.basiliskus.onda.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun capture(fake: FakePlaylistsSource, scheduler: TestCoroutineScheduler) =
        SongCaptureRepository(fake, clock = { LocalDateTime.of(2026, 7, 23, 21, 8) },
            ioDispatcher = StandardTestDispatcher(scheduler))

    @Test fun `capture appends one liked line to the titles log`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.Saved, r.capture("Artist - Title"))
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

        r.capture("Artist - Title")
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

        r.capture("Artist - Title")
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
        launch { r.capture("First - Song") }
        launch { r.capture("Second - Song") }
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

        assertEquals(SongCaptureRepository.Outcome.NoTitle, r.capture(null))
        assertEquals(0, fake.appends)
        assertTrue(fake.files.isEmpty())
    }

    @Test fun `blank stream title writes nothing`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.NoTitle, r.capture("   "))
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
        assertEquals(SongCaptureRepository.Outcome.WriteFailed, r.capture("Artist - Title"))
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
        assertEquals(SongCaptureRepository.Outcome.WriteFailed, r.capture("Artist - Title"))
        assertTrue(fake.files.isEmpty(), "a refused resolve left a shard: ${fake.files.keys}")
        assertEquals(1, fake.appends, "the failure must be an observed attempt, not a skipped one")
    }

    @Test fun `append failure after a successful capture leaves the earlier line intact`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.Saved, r.capture("Artist - Title"))
        fake.failAppends = true
        assertEquals(SongCaptureRepository.Outcome.WriteFailed, r.capture("Second - Song"))
        assertEquals(listOf(expectedLine), fake.titlesLogLines())
    }

    @Test fun `untrimmed title is written raw`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        assertEquals(SongCaptureRepository.Outcome.Saved, r.capture("  A - B  "))
        assertEquals("Jul 23 (Thu) 21:08 |       A - B   (LIKED)", fake.titlesLogLines().single())
    }

    @Test fun `captured line matches the pinned format literally`() = runTest {
        val fake = FakePlaylistsSource()
        val r = capture(fake, testScheduler)

        r.capture("Artist - Title")
        assertEquals(expectedLine, fake.titlesLogLines().single())
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
