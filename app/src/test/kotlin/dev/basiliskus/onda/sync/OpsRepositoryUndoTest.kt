package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.ops.Op
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Every compaction-algebra rule composed with delete→undo — the snapshot/generation-CAS
 * orchestration layer. The algebra itself is canon in :core's OpsCompactionTest; each row
 * here seeds a journal, deletes (capturing the token), asserts the compacted journal, then
 * undoes and asserts the restored ops round-tripped through the production serializer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpsRepositoryUndoTest {

    @Test fun `undo after delete of synced station restores empty journal`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.delete(s)
        advanceUntilIdle()
        val d = fake.journalOps().single() as Op.Delete
        assertEquals("http://u", d.url)
        assertEquals("stations", d.playlist)
        assertTrue(r.effectiveStations().none { it.url == "http://u" })

        r.undo(token)
        advanceUntilIdle()
        assertEquals("", fake.files[JOURNAL])
        assertEquals(emptyList(), fake.journalOps())
        assertTrue(r.effectiveStations().any { it.url == "http://u" })
    }

    @Test fun `undo after delete of pending targeted add restores the add`() = runTest {
        val add = addOp("http://u", "stations", "A")
        val fake = FakePlaylistsSource(ready())
        fake.seedJournal(add)
        val r = repo(fake)
        advanceUntilIdle()
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertTrue(row.pending)

        val token = r.delete(row)
        advanceUntilIdle()
        assertEquals(emptyList(), fake.journalOps()) // delete consumed the add
        assertEquals("", fake.files[JOURNAL])
        assertTrue(r.effectiveStations().isEmpty())

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(add), fake.journalOps())
        assertTrue(r.effectiveStations().single { it.url == "http://u" }.pending)
    }

    @Test fun `undo after delete of moved station restores the move`() = runTest {
        val move = moveOp("http://u", "stations", "B")
        val fake = FakePlaylistsSource(ready(station("http://u")))
        fake.seedJournal(move)
        val r = repo(fake)
        advanceUntilIdle()
        assertEquals("B", r.effectiveStations().single { it.url == "http://u" }.group)

        val token = r.delete(r.effectiveStations().single { it.url == "http://u" })
        advanceUntilIdle()
        assertIs<Op.Delete>(fake.journalOps().single())

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(move), fake.journalOps())
        val restored = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("B", restored.group)
        assertTrue(restored.pending)
    }

    @Test fun `undo after delete of duplicate-add residue restores newest add`() = runTest {
        val v1 = addOp("http://u", "stations", "A")
        val v2 = addOp("http://u", "stations", "B")
        val fake = FakePlaylistsSource(ready())
        fake.seedJournal(v1, v2)
        val r = repo(fake)
        advanceUntilIdle()
        assertEquals("B", r.effectiveStations().single().group) // compacted to v2

        val token = r.delete(r.effectiveStations().single())
        advanceUntilIdle()
        assertEquals(emptyList(), fake.journalOps())

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(v2), fake.journalOps()) // newest, not v1
    }

    @Test fun `undo after delete of re-added station restores delete-add pair`() = runTest {
        val d = deleteOp("http://u", "stations")
        val a = addOp("http://u", "stations", "A")
        val fake = FakePlaylistsSource(ready(station("http://u", group = "G")))
        fake.seedJournal(d, a)
        val r = repo(fake)
        advanceUntilIdle()
        assertEquals("A", r.effectiveStations().single { it.url == "http://u" }.group)

        val token = r.delete(r.effectiveStations().single { it.url == "http://u" })
        advanceUntilIdle()
        assertEquals(listOf<Op>(d), fake.journalOps()) // sole delete residue

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf(d, a), fake.journalOps()) // pair, in order
    }

    @Test fun `undo after delete of move-rewritten pending add restores the rewritten add`() = runTest {
        val a = addOp("http://u", "stations", "A")
        val fake = FakePlaylistsSource(ready())
        fake.seedJournal(a)
        val r = repo(fake)
        advanceUntilIdle()

        r.effectiveStations().single().let { r.moveToPlaylist(it, it.playlist, "B") }
        advanceUntilIdle()
        val rewritten = fake.journalOps().single() as Op.Add
        assertEquals(a.id, rewritten.id) // move rewrote the add in place
        assertEquals("B", rewritten.group)

        val token = r.delete(r.effectiveStations().single())
        advanceUntilIdle()
        assertEquals(emptyList(), fake.journalOps())

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(rewritten), fake.journalOps())
    }

    @Test fun `undo after redundant delete restores the sole delete residue`() = runTest {
        val d = deleteOp("http://u", "stations")
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        fake.seedJournal(d)
        val r = repo(fake)
        advanceUntilIdle()
        assertTrue(r.effectiveStations().none { it.url == "http://u" }) // already deleted

        // Unreachable by swipe (applyOps removed the row, so it can't be deleted twice);
        // driven at the seam with the same station value the seeded delete targeted.
        val token = r.delete(s)
        advanceUntilIdle()
        assertEquals(listOf<Op>(d), fake.journalOps()) // second delete swallowed, id and all

        // Undo-tautological by construction: delete-after-delete is idempotent, so the
        // residue and the rendered rows are identical with or without the undo. The row's
        // real subject is the compaction assertion above; keep it for that, not as undo
        // coverage.
        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(d), fake.journalOps())
        assertTrue(r.effectiveStations().none { it.url == "http://u" })
    }

    @Test fun `undo after delete of twice-moved station restores the last move`() = runTest {
        val fake = FakePlaylistsSource(ready(station("http://u")))
        val r = repo(fake)
        advanceUntilIdle()

        r.effectiveStations().single().let { r.moveToPlaylist(it, it.playlist, "B") }
        advanceUntilIdle()
        r.effectiveStations().single().let { r.moveToPlaylist(it, it.playlist, "C") }
        advanceUntilIdle()
        val move = fake.journalOps().single() as Op.Move
        assertEquals("C", move.toGroup) // last move wins, single residue

        val token = r.delete(r.effectiveStations().single())
        advanceUntilIdle()
        assertIs<Op.Delete>(fake.journalOps().single())

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(move), fake.journalOps()) // move→C, not →B and not absent
        val restored = r.effectiveStations().single()
        assertEquals("C", restored.group)
        assertTrue(restored.pending)
    }

    @Test fun `intervening mutation voids the undo token`() = runTest {
        val s1 = station("http://u1")
        val fake = FakePlaylistsSource(ready(s1))
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.delete(s1)
        advanceUntilIdle()
        r.toggle(rbStation("http://u2"))
        advanceUntilIdle()

        r.undo(token)
        advanceUntilIdle()
        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        assertEquals("http://u1", (ops[0] as Op.Delete).url)
        val add = ops[1] as Op.Add
        assertNull(add.playlist)
        assertEquals("http://u2", add.station.url)
        assertTrue(r.effectiveStations().none { it.url == "http://u1" }) // undo was a no-op
    }

    // The row `refresh voids the undo token even with identical content` is gone by
    // design: it pinned the defect PR #66's review confirmed (a no-op foreground tick
    // killing a live token, then #18's feedback calling it "Nothing to undo"). Its
    // inversion lives in UndoIntegrityTest."a content-identical refresh does not void a
    // live undo token"; a CHANGED refresh still voiding is pinned by
    // UndoIntegrityTest."a voided undo reports Nothing to undo instead of silently no-oping"
    // and by OpsRepositoryMoveTest."refresh voids the move token".

    @Test fun `second undo is a no-op`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        val errors = collectErrors(r)
        advanceUntilIdle()

        val token = r.delete(s)
        advanceUntilIdle()
        r.undo(token)
        advanceUntilIdle()
        val writesAfterFirstUndo = fake.writes

        r.undo(token)
        advanceUntilIdle()
        // The two post-undo states are identical, so only the absence of a write
        // distinguishes a no-op from a repeated restore — this is the row that pins the
        // generation CAS against undo's own setOps bump. And silence: a redundant
        // dispatch after the token's own successful undo is not a lost tap, so it must
        // NOT claim "Nothing to undo" about an undo that plainly happened (the consumed
        // flag distinguishes it from a genuinely voided tap — PR #66 review).
        assertEquals(writesAfterFirstUndo, fake.writes)
        assertEquals(emptyList(), fake.journalOps())
        assertTrue(r.effectiveStations().any { it.url == "http://u" })
        assertEquals(emptyList(), errors)
    }

    @Test fun `rolled-back delete auto-voids its token`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        val errors = collectErrors(r)
        advanceUntilIdle()

        fake.failWrites = true
        val token = r.delete(s)
        advanceUntilIdle()
        assertTrue(r.effectiveStations().any { it.url == "http://u" }) // rolled back
        assertEquals(listOf(UNAVAILABLE_MESSAGE), errors)
        assertNull(fake.files[JOURNAL]) // write never landed

        fake.failWrites = false
        val writesAfterRollback = fake.writes
        r.undo(token)
        advanceUntilIdle()
        assertEquals(writesAfterRollback, fake.writes) // token voided: undo never reached the write
        assertNull(fake.files[JOURNAL])
        assertTrue(r.effectiveStations().any { it.url == "http://u" })
        // The failed write's report, then the voided tap's own (#18).
        assertEquals(listOf(UNAVAILABLE_MESSAGE, UNDO_VOIDED_MESSAGE), errors)
    }

    @Test fun `failed undo write rolls back to post-delete ops`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        val errors = collectErrors(r)
        advanceUntilIdle()

        val token = r.delete(s)
        advanceUntilIdle()
        fake.failWrites = true
        r.undo(token)
        advanceUntilIdle()
        assertIs<Op.Delete>(fake.journalOps().single()) // file still holds the delete
        assertTrue(r.effectiveStations().none { it.url == "http://u" }) // ops rolled back
        assertEquals(listOf(UNAVAILABLE_MESSAGE), errors)

        // A failed undo leaves the token RETRYABLE: the rollback's own bumps must not
        // void it, or the retry tap would claim "Nothing to undo" about a delete that
        // plainly stands (PR #66 round 3). Once the folder is back, the retry restores.
        fake.failWrites = false
        r.undo(token)
        advanceUntilIdle()
        assertEquals(emptyList(), fake.journalOps(), "the retry must restore the pre-delete journal")
        assertTrue(r.effectiveStations().any { it.url == "http://u" })
        assertEquals(listOf(UNAVAILABLE_MESSAGE), errors, "a successful retry has nothing further to report")
    }
}
