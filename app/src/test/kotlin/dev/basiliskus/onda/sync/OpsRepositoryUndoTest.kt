package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.ops.Op
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Every compaction-algebra rule composed with delete→undo — the snapshot/generation-CAS
 * orchestration layer. The algebra itself is canon in :core's OpsCompactionTest; each row
 * here seeds a journal, deletes (capturing the token), asserts the compacted journal, then
 * undoes and asserts the restored ops round-tripped through the production serializer.
 */
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

        r.move(r.effectiveStations().single(), "B")
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

    @Test fun `refresh voids the undo token even with identical content`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.delete(s)
        advanceUntilIdle()
        r.refresh()
        advanceUntilIdle()

        r.undo(token)
        advanceUntilIdle()
        assertIs<Op.Delete>(fake.journalOps().single())
        assertTrue(r.effectiveStations().none { it.url == "http://u" })
    }

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
        r.undo(token)
        advanceUntilIdle()
        assertEquals(emptyList(), fake.journalOps())
        assertTrue(r.effectiveStations().any { it.url == "http://u" })
        assertTrue(errors.isEmpty())
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
        r.undo(token)
        advanceUntilIdle()
        assertNull(fake.files[JOURNAL]) // token was voided: no write
        assertTrue(r.effectiveStations().any { it.url == "http://u" })
        assertEquals(1, errors.size)
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
    }
}
