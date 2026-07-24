package dev.basiliskus.onda.core.ops

import kotlin.test.*

class OpsJournalTest {
    private val add = Op.Add(
        id = "a1", ts = "2026-07-24T10:00:00Z",
        station = OpStation(
            name = "KEXP", url = "https://kexp.example/stream", icon = "https://kexp.example/logo.png",
            stationuuid = "uuid-1", tags = "eclectic", country = "USA", votes = 42,
        ),
        playlist = "stations", group = "College Radio",
    )
    private val quickAdd = Op.Add(
        id = "a2", ts = "2026-07-24T10:01:00Z",
        station = OpStation(name = "FIP", url = "https://fip.example/stream"),
    )
    private val delete = Op.Delete(
        id = "d1", ts = "2026-07-24T10:02:00Z",
        url = "https://gone.example/stream", playlist = "stations",
    )
    private val move = Op.Move(
        id = "m1", ts = "2026-07-24T10:03:00Z",
        url = "https://kexp.example/stream", playlist = "stations", toGroup = "Review ▲ Promising",
    )

    @Test fun `serialize then parse round-trips all three op shapes`() {
        val ops = listOf(add, quickAdd, delete, move)
        val result = OpsJournal.parse(OpsJournal.serialize(ops))
        assertEquals(ops, result.ops)
        assertEquals(0, result.malformed)
    }

    @Test fun `discriminator is op and nulls are explicit`() {
        val lines = OpsJournal.serialize(listOf(quickAdd, delete, move)).trim().lines()
        assertTrue(lines[0].contains("\"op\":\"add\""), lines[0])
        assertTrue(lines[0].contains("\"playlist\":null"), lines[0])
        assertTrue(lines[0].contains("\"group\":null"), lines[0])
        assertTrue(lines[1].contains("\"op\":\"delete\""), lines[1])
        assertTrue(lines[2].contains("\"op\":\"move\""), lines[2])
        assertTrue(lines[2].contains("\"to_group\":"), lines[2])
    }

    @Test fun `serialize emits one line per op with trailing newline`() {
        val text = OpsJournal.serialize(listOf(add, delete))
        assertTrue(text.endsWith("\n"))
        assertEquals(2, text.trim().lines().size)
        assertEquals("", OpsJournal.serialize(emptyList()))
    }

    @Test fun `parse skips garbage line and counts it malformed`() {
        val text = OpsJournal.serialize(listOf(add)) + "not json at all\n" + OpsJournal.serialize(listOf(delete))
        val result = OpsJournal.parse(text)
        assertEquals(listOf<Op>(add, delete), result.ops)
        assertEquals(1, result.malformed)
    }

    @Test fun `parse skips truncated json tail (partial line mid-sync)`() {
        val full = OpsJournal.serialize(listOf(add, delete))
        val truncated = full.trim().let { it.substring(0, it.length - 10) }
        val result = OpsJournal.parse(truncated)
        assertEquals(listOf<Op>(add), result.ops)
        assertEquals(1, result.malformed)
    }

    @Test fun `parse skips unknown op kind but keeps surrounding ops`() {
        val unknown = """{"op":"rename","id":"x1","ts":"2026-07-24T10:04:00Z","url":"http://u","playlist":"stations"}"""
        val text = OpsJournal.serialize(listOf(add)) + unknown + "\n" + OpsJournal.serialize(listOf(move))
        val result = OpsJournal.parse(text)
        assertEquals(listOf<Op>(add, move), result.ops)
        assertEquals(1, result.malformed)
    }

    @Test fun `parse ignores unknown fields (forward compat)`() {
        val line = """{"op":"delete","id":"d1","ts":"2026-07-24T10:02:00Z","url":"https://gone.example/stream","playlist":"stations","note":"future field"}"""
        val result = OpsJournal.parse(line)
        assertEquals(listOf<Op>(delete), result.ops)
        assertEquals(0, result.malformed)
    }

    @Test fun `parse of empty and blank-line text yields no ops, no malformed`() {
        for (text in listOf("", "\n", "\n\n  \n")) {
            val result = OpsJournal.parse(text)
            assertEquals(emptyList(), result.ops)
            assertEquals(0, result.malformed)
        }
    }
}
