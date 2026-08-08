package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.ops.Op
import dev.basiliskus.onda.core.ops.OpStation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Cross-playlist move: the Delete(source) + Add(target) pair emitted from a single
 * mutate, its single undo token, and every compaction-algebra rule the pair composes
 * with. The algebra itself is canon in :core's OpsCompactionTest; these rows drive it
 * through the repository seam and assert on the persisted journal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpsRepositoryMoveTest {

    /** A source playlist holding [s] plus a non-empty "nts" — applyOps never creates a
     *  playlist, so the move target must already exist for the add to surface. */
    private fun sourceAndTarget(s: Station) = ready(s, station("http://filler", "nts", "N"))

    /** Same, for rows whose moved row is a pending add rather than a synced station: the
     *  source playlist still has to exist for the seeded add to surface. */
    private fun bothPlaylists() = ready(station("http://s0", "stations", "A"), station("http://filler", "nts", "N"))

    private fun logoStation(url: String) = Station(
        name = "S $url", url = url, group = "A", playlist = "stations", logo = "http://logo",
    )

    @Test fun `cross-playlist move writes the pair in one journal write`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()
        val writesBefore = fake.writes

        r.moveToPlaylist(s, "nts", "Jazz")
        advanceUntilIdle()

        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        val d = ops[0] as Op.Delete
        assertEquals("http://u", d.url)
        assertEquals("stations", d.playlist)
        val a = ops[1] as Op.Add
        assertEquals("http://u", a.station.url)
        assertEquals("nts", a.playlist)
        assertEquals("Jazz", a.group)
        // The crux: both ops land in ONE write. Two mutates would write twice, exposing a
        // deleted-but-not-added journal to the desktop merge in between.
        assertEquals(writesBefore + 1, fake.writes)
    }

    /** The journal-side contract of GitHub #38: the pair carries the write's shared batch id
     *  with strictly increasing ts, so the desktop can enforce the atomicity the phone
     *  guarantees in memory. Byte shape (absent key on old ops) is canon in OpsJournalTest;
     *  these rows pin what the REPOSITORY stamps. */
    @Test fun `cross-playlist pair shares one batch id with strictly increasing ts`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "Jazz")
        advanceUntilIdle()

        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        val batch = ops[0].batch
        assertNotNull(batch, "delete carries no batch id")
        assertEquals(batch, ops[1].batch, "pair does not share the write's batch id")
        val (d, a) = ops.map { java.time.Instant.parse(it.ts) }
        assertTrue(d < a, "ts not strictly increasing within the batch: $d !< $a")
    }

    @Test fun `separate mutates mint distinct batch ids and a single-op mutate still carries one`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s, station("http://u2")))
        val r = repo(fake)
        advanceUntilIdle()

        r.delete(s)
        advanceUntilIdle()
        r.delete(station("http://u2"))
        advanceUntilIdle()

        val batches = fake.journalOps().map { it.batch }
        assertTrue(batches.all { it != null }, "a single-op mutate lost its batch id: $batches")
        assertEquals(2, batches.toSet().size, "distinct writes share a batch id: $batches")
    }

    @Test fun `journal bytes carry batch for new ops and no batch key for pre-existing ones`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        // Seed a pre-#38 journal line: parsed on refresh, batch stays null, and the rewrite
        // must not invent a key for it.
        fake.seedJournal(addOp("http://old"))
        val r = repo(fake)
        advanceUntilIdle()

        r.delete(s)
        advanceUntilIdle()

        val lines = fake.files.getValue(JOURNAL).trim().lines()
        val old = lines.single { it.contains("http://old") }
        val new = lines.single { it.contains("\"op\":\"delete\"") }
        assertFalse(old.contains("\"batch\""), "rewrite invented a batch key on a pre-#38 op: $old")
        assertTrue(new.contains("\"batch\""), "new op serialized without its batch id: $new")
    }

    @Test fun `cross-playlist move arms one token that undoes both ops`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.moveToPlaylist(s, "nts", "Jazz")
        advanceUntilIdle()
        assertEquals(2, fake.journalOps().size)

        r.undo(token)
        advanceUntilIdle()
        // One token, both ops gone. Split into two mutates and the delete's token is stale
        // by the time the add lands, so this undo would leave the add standing.
        assertEquals(emptyList(), fake.journalOps())
        assertEquals("", fake.files[JOURNAL])
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("stations", row.playlist)
        assertFalse(row.pending)
    }

    @Test fun `same-playlist target emits a single move op and no add`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()
        val writesBefore = fake.writes

        r.moveToPlaylist(s, "stations", "B")
        advanceUntilIdle()

        // Not the lossy pair: the desktop applies a Move by re-inserting the CSV line
        // verbatim, while delete+add re-creates it from name/url/icon.
        val move = fake.journalOps().single() as Op.Move
        assertEquals("http://u", move.url)
        assertEquals("stations", move.playlist)
        assertEquals("B", move.toGroup)
        // …and never a silent no-op: an op is emitted and a write happens.
        assertEquals(writesBefore + 1, fake.writes)
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("B", row.group)
        assertTrue(row.pending)
    }

    @Test fun `moved station renders pending in the target and gone from the source`() = runTest {
        val s = logoStation("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "Jazz")
        advanceUntilIdle()

        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("nts", row.playlist)
        assertEquals("Jazz", row.group)
        assertTrue(row.pending)
        assertEquals(s.name, row.name)
        assertEquals("http://logo", row.logo)
    }

    @Test fun `add op carries the playlist station's icon and empty radio-browser extras`() = runTest {
        val s = logoStation("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "Jazz")
        advanceUntilIdle()

        // Spelled out rather than relying on OpStation's defaults: a playlist station has
        // no radio-browser identity and none may be fabricated for it.
        assertEquals(
            OpStation(
                name = "S http://u", url = "http://u", icon = "http://logo",
                stationuuid = "", tags = "", country = "", votes = 0,
            ),
            (fake.journalOps()[1] as Op.Add).station,
        )
    }

    @Test fun `same-playlist move is undoable through the returned token`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.moveToPlaylist(s, "stations", "B")
        advanceUntilIdle()
        assertIs<Op.Move>(fake.journalOps().single())

        r.undo(token)
        advanceUntilIdle()
        assertEquals(emptyList(), fake.journalOps())
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("A", row.group)
        assertFalse(row.pending)
    }

    @Test fun `move of a pending targeted add leaves only the add in the target`() = runTest {
        val seeded = addOp("http://u", "stations", "X")
        val fake = FakePlaylistsSource(bothPlaylists())
        fake.seedJournal(seeded)
        val r = repo(fake)
        advanceUntilIdle()
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertTrue(row.pending)

        val token = r.moveToPlaylist(row, "nts", "G")
        advanceUntilIdle()
        // The pair's delete consumed the pending add: one op, not three.
        val a = fake.journalOps().single() as Op.Add
        assertEquals("nts", a.playlist)
        assertEquals("G", a.group)

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(seeded), fake.journalOps()) // snapshot restore, original id
    }

    @Test fun `move of a moved station replaces the pending move with the pair`() = runTest {
        val seeded = moveOp("http://u", "stations", "X")
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        fake.seedJournal(seeded)
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.moveToPlaylist(r.effectiveStations().single { it.url == "http://u" }, "nts", "G")
        advanceUntilIdle()
        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        assertIs<Op.Delete>(ops[0])
        assertEquals("nts", (ops[1] as Op.Add).playlist)

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(seeded), fake.journalOps())
    }

    @Test fun `move after a pending delete keeps the older delete and adds to the target`() = runTest {
        val seeded = deleteOp("http://u", "stations")
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        fake.seedJournal(seeded)
        val r = repo(fake)
        advanceUntilIdle()
        assertTrue(r.effectiveStations().none { it.url == "http://u" }) // already deleted

        // Unreachable by long-press (the row is gone from the UI); driven at the seam with
        // the same station value the seeded delete targeted.
        val token = r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()
        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        assertEquals(seeded, ops[0]) // second delete swallowed, id and all
        assertEquals("nts", (ops[1] as Op.Add).playlist)

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(seeded), fake.journalOps())
    }

    @Test fun `move into a playlist holding a pending add of the same url replaces that add`() = runTest {
        val seeded = addOp("http://u", "nts", "X")
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        fake.seedJournal(seeded)
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()

        // The pair is NOT adjacent and the add precedes its partner delete: the target key
        // already had a residue, so key-insertion order puts it first. Nothing may assume
        // adjacency or pair ordering in the serialized journal.
        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        val a = ops[0] as Op.Add
        assertEquals("nts", a.playlist)
        assertEquals("G", a.group) // replaced the seeded add, not appended beside it
        assertEquals("stations", (ops[1] as Op.Delete).playlist)
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("nts", row.playlist)
        assertEquals("G", row.group)
    }

    @Test fun `move into a playlist holding a pending delete of the same url keeps all three ops`() = runTest {
        val seeded = deleteOp("http://u", "nts")
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        fake.seedJournal(seeded)
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()

        val ops = fake.journalOps()
        assertEquals(3, ops.size)
        assertEquals(seeded, ops[0]) // delete-then-re-add in the target: appended, not collapsed
        assertEquals("nts", (ops[1] as Op.Add).playlist)
        assertEquals("stations", (ops[2] as Op.Delete).playlist)
    }

    @Test fun `move into a playlist holding a pending move of the same url appends the add after that move`() = runTest {
        val seeded = moveOp("http://u", "nts", "X")
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        fake.seedJournal(seeded)
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()

        // The target key's residue is a Move, so the add takes compaction's append branch:
        // an Add never rewrites a Move the way a Move rewrites an Add. All three ops stand,
        // and the pair is again non-adjacent with the add ahead of its partner delete.
        val ops = fake.journalOps()
        assertEquals(3, ops.size)
        assertEquals(seeded, ops[0])
        val a = ops[1] as Op.Add
        assertEquals("nts", a.playlist)
        assertEquals("G", a.group)
        assertEquals("stations", (ops[2] as Op.Delete).playlist)
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("nts", row.playlist)
        assertEquals("G", row.group)
        assertTrue(row.pending)

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(seeded), fake.journalOps())
    }

    @Test fun `same-playlist move after a pending delete keeps the delete and appends the move`() = runTest {
        val seeded = deleteOp("http://u", "stations")
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        fake.seedJournal(seeded)
        val r = repo(fake)
        advanceUntilIdle()
        assertTrue(r.effectiveStations().none { it.url == "http://u" }) // already deleted

        // Same-playlist target, so a single Move op — onto a key whose residue is a Delete.
        // Compaction appends it rather than replacing the delete, which is what keeps a Move
        // from resurrecting a deleted row. The desktop does NOT backstop this: if the delete
        // were replaced, apply_move would find the url still in the CSV and silently restore
        // the row. It is the shipped [Delete, Move] order that leaves apply_move nothing to
        // find, so the move lands in ops-rejected.jsonl instead. The ordering is the defence.
        val token = r.moveToPlaylist(s, "stations", "B")
        advanceUntilIdle()

        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        assertEquals(seeded, ops[0])
        val move = ops[1] as Op.Move
        assertEquals("stations", move.playlist)
        assertEquals("B", move.toGroup)
        assertTrue(r.effectiveStations().none { it.url == "http://u" }) // still gone from the overlay

        r.undo(token)
        advanceUntilIdle()
        assertEquals(listOf<Op>(seeded), fake.journalOps())
    }

    @Test fun `chained cross-playlist moves keep one delete and one add`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(
            ready(s, station("http://filler", "nts", "N"), station("http://filler2", "fip", "F"))
        )
        val r = repo(fake)
        advanceUntilIdle()

        val token1 = r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()
        val firstDelete = fake.journalOps()[0] as Op.Delete
        val token2 = r.moveToPlaylist(r.effectiveStations().single { it.url == "http://u" }, "fip", "H")
        advanceUntilIdle()

        // The middle playlist leaves no trace: its add and its delete net to nothing.
        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        assertEquals(firstDelete, ops[0])
        assertEquals("fip", (ops[1] as Op.Add).playlist)

        r.undo(token2)
        advanceUntilIdle()
        assertEquals("nts", (fake.journalOps()[1] as Op.Add).playlist)

        val writesAfterUndo = fake.writes
        r.undo(token1)
        advanceUntilIdle()
        assertEquals(writesAfterUndo, fake.writes) // token1 voided by the second move's bump
        assertEquals(2, fake.journalOps().size)
    }

    @Test fun `round trip back to the source nets to a delete and re-add there`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()
        r.moveToPlaylist(r.effectiveStations().single { it.url == "http://u" }, "stations", "H")
        advanceUntilIdle()

        // A→B→A is not identity — the second move's source is B, so it is a cross-playlist
        // move like any other and the source row is dropped and re-created in group H.
        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        assertEquals("stations", (ops[0] as Op.Delete).playlist)
        val a = ops[1] as Op.Add
        assertEquals("stations", a.playlist)
        assertEquals("H", a.group)
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("stations", row.playlist)
        assertEquals("H", row.group)
        assertTrue(row.pending)
    }

    @Test fun `delete after a cross-playlist move nets to the source delete`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        val moveToken = r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()
        val pair = fake.journalOps()

        val deleteToken = r.delete(r.effectiveStations().single { it.url == "http://u" })
        advanceUntilIdle()
        assertEquals(listOf(pair[0]), fake.journalOps()) // the add is consumed, the source delete stands

        r.undo(deleteToken)
        advanceUntilIdle()
        assertEquals(pair, fake.journalOps()) // whole pair back, ids and all

        val writesAfterUndo = fake.writes
        r.undo(moveToken)
        advanceUntilIdle()
        assertEquals(writesAfterUndo, fake.writes) // voided by the delete's bump
        assertEquals(pair, fake.journalOps())
    }

    @Test fun `group move inside the target rewrites the pending add`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()
        val addId = (fake.journalOps()[1] as Op.Add).id

        val moved = r.effectiveStations().single { it.url == "http://u" }
        r.moveToPlaylist(moved, moved.playlist, "H")
        advanceUntilIdle()

        val ops = fake.journalOps()
        assertEquals(2, ops.size)
        val a = ops[1] as Op.Add
        assertEquals("H", a.group)
        assertEquals(addId, a.id) // rewritten in place, not appended as a Move
    }

    @Test fun `refresh voids the move token`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        val token = r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()
        val pair = fake.journalOps()
        // A genuinely-changed journal: a content-identical refresh no longer voids
        // (UndoIntegrityTest pins that), so the desktop appends an op first.
        val appended = addOp("http://z")
        fake.seedJournal(*(pair + appended).toTypedArray())
        r.refresh()
        advanceUntilIdle()
        val writesAfterRefresh = fake.writes

        r.undo(token)
        advanceUntilIdle()
        // Only the absence of a write distinguishes a voided token from a restore that
        // happens to rewrite the same bytes.
        assertEquals(writesAfterRefresh, fake.writes)
        assertEquals(pair + appended, fake.journalOps())
    }

    @Test fun `failed write rolls back the whole pair and voids its token`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        val errors = collectErrors(r)
        advanceUntilIdle()

        fake.failWrites = true
        val token = r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("stations", row.playlist) // neither half of the pair survived
        assertFalse(row.pending)
        assertEquals(listOf(UNAVAILABLE_MESSAGE), errors)
        assertNull(fake.files[JOURNAL])
        assertEquals(1, fake.writes)

        fake.failWrites = false
        r.undo(token)
        advanceUntilIdle()
        assertEquals(1, fake.writes) // the rollback's own bump voided the token
        assertNull(fake.files[JOURNAL])
        // The failed write's report, then the voided tap's own (#18).
        assertEquals(listOf(UNAVAILABLE_MESSAGE, UNDO_VOIDED_MESSAGE), errors)
    }

    @Test fun `quick-star add survives a cross-playlist move of the same url`() = runTest {
        val quick = addOp("http://u")
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        fake.seedJournal(quick)
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()

        val ops = fake.journalOps()
        assertEquals(3, ops.size)
        assertEquals(quick, ops[0]) // (url, null) is a key of its own
        assertIs<Op.Delete>(ops[1])
        assertIs<Op.Add>(ops[2])
        assertEquals(setOf("http://u"), r.starredUrls.value)
    }

    @Test fun `move into a playlist that already holds the url adds no duplicate row`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s, station("http://u", "nts", "N")))
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()

        assertEquals(2, fake.journalOps().size) // the journal still carries the pair
        // …but the overlay's dup-add backstop drops the add, so the move degenerates to
        // "removed from the source" rather than showing the station twice.
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("nts", row.playlist)
        assertEquals("N", row.group)
        assertFalse(row.pending)
    }

    @Test fun `group move emits a single move op and offers undo`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()
        val writesBefore = fake.writes

        r.moveToPlaylist(s, s.playlist, "B")
        advanceUntilIdle()

        val move = fake.journalOps().single() as Op.Move
        assertEquals("B", move.toGroup)
        assertEquals(writesBefore + 1, fake.writes)
    }

    @Test fun `repeated identical move is idempotent in the journal`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(sourceAndTarget(s))
        val r = repo(fake)
        advanceUntilIdle()

        r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()
        val first = fake.journalOps()
        // The same stale Station value a double-tapped sheet would hand back.
        val token2 = r.moveToPlaylist(s, "nts", "G")
        advanceUntilIdle()

        val ops = fake.journalOps()
        assertEquals(2, ops.size) // second delete swallowed, second add replaced the first
        assertEquals(first[0], ops[0])

        r.undo(token2)
        advanceUntilIdle()
        assertEquals(first, fake.journalOps())
    }
}
