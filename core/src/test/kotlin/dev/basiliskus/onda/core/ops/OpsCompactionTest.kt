package dev.basiliskus.onda.core.ops

import kotlin.test.*

class OpsCompactionTest {
    private var seq = 0
    private fun nextId() = "id-${seq++}"
    private fun nextTs() = "2026-07-24T10:00:%02dZ".format(seq)

    private fun add(url: String, playlist: String? = null, group: String? = null) =
        Op.Add(nextId(), nextTs(), OpStation(name = "S $url", url = url), playlist, group)

    private fun delete(url: String, playlist: String) = Op.Delete(nextId(), nextTs(), url, playlist)

    private fun move(url: String, playlist: String, toGroup: String) =
        Op.Move(nextId(), nextTs(), url, playlist, toGroup)

    @Test fun `add then delete same key nets to nothing`() {
        val ops = listOf(add("http://u", "stations", "G"), delete("http://u", "stations"))
        assertEquals(emptyList(), OpsJournal.compact(ops))
    }

    @Test fun `move then move last wins`() {
        val second = move("http://u", "stations", "B")
        val ops = listOf(move("http://u", "stations", "A"), second)
        assertEquals(listOf<Op>(second), OpsJournal.compact(ops))
    }

    @Test fun `delete then re-add keeps both in order`() {
        val d = delete("http://u", "stations")
        val a = add("http://u", "stations", "G")
        assertEquals(listOf(d, a), OpsJournal.compact(listOf(d, a)))
    }

    @Test fun `duplicate adds collapse to newest`() {
        val first = add("http://u", "stations", "A")
        val second = add("http://u", "stations", "B")
        assertEquals(listOf<Op>(second), OpsJournal.compact(listOf(first, second)))
    }

    @Test fun `move after pending add rewrites the add's group`() {
        val a = add("http://u", "stations", "A")
        val m = move("http://u", "stations", "B")
        val residue = OpsJournal.compact(listOf(a, m))
        assertEquals(listOf<Op>(a.copy(group = "B", ts = m.ts)), residue)
    }

    @Test fun `delete after move keeps only delete`() {
        val d = delete("http://u", "stations")
        assertEquals(listOf<Op>(d), OpsJournal.compact(listOf(move("http://u", "stations", "A"), d)))
    }

    @Test fun `null-playlist add unaffected by delete in a playlist (distinct keys)`() {
        val a = add("http://u")
        val d = delete("http://u", "stations")
        assertEquals(listOf(a, d), OpsJournal.compact(listOf(a, d)))
    }

    @Test fun `quick-star add and targeted add of same url coexist`() {
        val quick = add("http://u")
        val targeted = add("http://u", "stations", "G")
        assertEquals(listOf<Op>(quick, targeted), OpsJournal.compact(listOf(quick, targeted)))
    }

    @Test fun `delete then re-add then delete nets to one delete`() {
        val d1 = delete("http://u", "stations")
        val ops = listOf(d1, add("http://u", "stations", "G"), delete("http://u", "stations"))
        assertEquals(listOf<Op>(d1), OpsJournal.compact(ops))
    }

    @Test fun `move after delete-then-re-add rewrites the re-add`() {
        val d = delete("http://u", "stations")
        val a = add("http://u", "stations", "A")
        val m = move("http://u", "stations", "B")
        assertEquals(listOf(d, a.copy(group = "B", ts = m.ts)), OpsJournal.compact(listOf(d, a, m)))
    }

    @Test fun `duplicate deletes collapse to one`() {
        val d1 = delete("http://u", "stations")
        assertEquals(listOf<Op>(d1), OpsJournal.compact(listOf(d1, delete("http://u", "stations"))))
    }

    @Test fun `residues keep first-appearance key order`() {
        val a1 = add("http://one", "stations", "G")
        val d2 = delete("http://two", "nts")
        val m1 = move("http://one", "stations", "H")
        val residue = OpsJournal.compact(listOf(a1, d2, m1))
        assertEquals(listOf(a1.copy(group = "H", ts = m1.ts), d2), residue)
    }

    @Test fun `cross-playlist delete and add are distinct residues`() {
        val d = delete("http://u", "stations")
        val a = add("http://u", "nts", "G")
        // Emission order: the pair leaves OpsRepository as delete-then-add.
        assertEquals(listOf(d, a), OpsJournal.compact(listOf(d, a)))
        // Serialized order when the target key already had a residue (the add is keyed
        // ahead of its partner delete) — re-compacting that journal must not net the pair
        // away. This is the ordering that bites if the residue key drops the playlist:
        // on one key, add-then-delete collapses to nothing.
        assertEquals(listOf(a, d), OpsJournal.compact(listOf(a, d)))
    }

    @Test fun `move between playlists and back nets to a delete and re-add in the source`() {
        val d1 = delete("http://u", "stations")
        val a1 = add("http://u", "nts", "G")
        val d2 = delete("http://u", "nts")
        val a2 = add("http://u", "stations", "H")
        // A→B→A is not identity: the source row is dropped and re-created at the end of
        // group H, not restored where it sat.
        assertEquals(listOf(d1, a2), OpsJournal.compact(listOf(d1, a1, d2, a2)))
    }

    @Test fun `compact is idempotent`() {
        val ops = listOf(
            add("http://one", "stations", "A"),
            add("http://two"),
            delete("http://one", "stations"),
            delete("http://three", "nts"),
            add("http://three", "nts", "Live"),
            move("http://three", "nts", "Mixtapes"),
            move("http://four", "fip", "X"),
        )
        val once = OpsJournal.compact(ops)
        assertEquals(once, OpsJournal.compact(once))
    }
}
