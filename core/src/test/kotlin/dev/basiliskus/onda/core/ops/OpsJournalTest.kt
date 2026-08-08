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

    @Test fun `batch round-trips through serialize and parse on all three shapes`() {
        val ops = listOf(
            add.copy(batch = "b-1"), delete.copy(batch = "b-1"), move.copy(batch = "b-2"),
        )
        assertEquals(ops, OpsJournal.parse(OpsJournal.serialize(ops)).ops)
    }

    @Test fun `a null batch emits no batch key while playlist and group nulls stay explicit`() {
        // Absent-key is the contract: a pre-#38 op and a batchless line must be the same
        // bytes. playlist/group keep their explicit nulls (contract sample) — the
        // @EncodeDefault(NEVER) narrowing is batch's alone.
        // All three shapes: the annotation lives per-property, so an Add-only row would let
        // a dropped @EncodeDefault on Delete or Move ship green (Code Review mutation M8).
        OpsJournal.serialize(listOf(quickAdd, delete, move)).trim().lines().forEach { line ->
            assertFalse(line.contains("\"batch\""), line)
        }
        val line = OpsJournal.serialize(listOf(quickAdd)).trim()
        assertTrue(line.contains("\"playlist\":null"), line)
        assertTrue(line.contains("\"group\":null"), line)
        val stamped = OpsJournal.serialize(
            listOf(quickAdd.copy(batch = "b-9"), delete.copy(batch = "b-9"), move.copy(batch = "b-9")),
        ).trim().lines()
        stamped.forEach { assertTrue(it.contains("\"batch\":\"b-9\""), it) }
    }

    @Test fun `a pre-batch journal line parses with a null batch`() {
        // Raw fixture, not serialize() output — this is the byte shape already on phones.
        val line = """{"op":"delete","id":"d9","ts":"2026-07-01T00:00:00Z",""" +
            """"url":"https://old.example/s","playlist":"stations"}"""
        val result = OpsJournal.parse(line)
        assertEquals(0, result.malformed)
        assertNull((result.ops.single() as Op.Delete).batch)
    }

    @Test fun `stamp shares one batch across new ops with strictly increasing ts in list order`() {
        val base = java.time.Instant.parse("2026-08-07T12:00:00Z")
        val stamped = OpsJournal.stamp(
            listOf(delete, quickAdd, move), priorIds = emptySet(), batch = "b-7", baseTs = base,
        )
        assertEquals(listOf("d1", "a2", "m1"), stamped.map { it.id }, "order must be preserved")
        assertTrue(stamped.all { it.batch == "b-7" }, "batch not shared: $stamped")
        val instants = stamped.map { java.time.Instant.parse(it.ts) }
        assertEquals(base, instants.first())
        instants.zipWithNext().forEach { (a, b) -> assertTrue(a < b, "ts not strictly increasing: $instants") }
    }

    @Test fun `stamp leaves prior ops untouched, batch included`() {
        val old = delete.copy(batch = "b-old")
        val stamped = OpsJournal.stamp(
            listOf(old, quickAdd), priorIds = setOf("d1"),
            batch = "b-new", baseTs = java.time.Instant.parse("2026-08-07T12:00:00Z"),
        )
        assertSame(old, stamped[0])
        assertEquals("b-new", stamped[1].batch)
        assertEquals("2026-08-07T12:00:00.000Z", stamped[1].ts)
    }

    @Test fun `stamp floors its base above every parseable prior ts`() {
        // A write landing inside an earlier batch's +index span (or after a clock step-back)
        // must still sort after it — chronologically, and byte-wise among stamped ts.
        val prior = delete.copy(ts = "2026-08-07T12:00:05Z", batch = "b-old")
        val stamped = OpsJournal.stamp(
            listOf(prior, quickAdd), priorIds = setOf("d1"),
            batch = "b-new", baseTs = java.time.Instant.parse("2026-08-07T12:00:00Z"),
        )
        assertEquals("2026-08-07T12:00:05.001Z", stamped[1].ts, "base not floored above the prior ts")
        val garbage = delete.copy(ts = "not-a-timestamp")
        val stampedPastGarbage = OpsJournal.stamp(
            listOf(garbage, quickAdd), priorIds = setOf("d1"),
            batch = "b-new", baseTs = java.time.Instant.parse("2026-08-07T12:00:00Z"),
        )
        assertEquals("2026-08-07T12:00:00.000Z", stampedPastGarbage[1].ts, "unparseable prior ts must be ignored")
    }

    @Test fun `stamped ts is fixed-width millis, byte-order consistent with its siblings`() {
        // Instant.toString drops a zero fraction ("…:00Z"), which sorts lexicographically
        // AFTER "…:00.001Z" — so an exact-second base must still emit .000.
        val stamped = OpsJournal.stamp(
            listOf(delete, quickAdd), priorIds = emptySet(),
            batch = "b-7", baseTs = java.time.Instant.parse("2026-08-07T12:00:00Z"),
        )
        val pattern = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$""")
        stamped.forEach { assertTrue(pattern.matches(it.ts), "ts not fixed-width: ${it.ts}") }
        assertEquals(listOf("2026-08-07T12:00:00.000Z", "2026-08-07T12:00:00.001Z"), stamped.map { it.ts })
        assertTrue(stamped[0].ts < stamped[1].ts, "stamped ts not byte-order increasing")
    }

    @Test fun `compaction keeps a surviving op's batch`() {
        val kept = OpsJournal.compact(listOf(delete.copy(batch = "b-3"), quickAdd.copy(batch = "b-4")))
        assertEquals(listOf("b-3", "b-4"), kept.map { it.batch })
    }

    @Test fun `compaction's move-rewrites-add keeps the add's batch and takes the move's ts`() {
        // The one rewrite branch that mixes writes: a pending add from write 1 group-moved in
        // write 2 survives as ONE add carrying its introducing write's batch with the move's
        // ts — "ops compaction keeps carry the batch of the write that introduced them"
        // (OpsRepository.mutate) is exactly this claim.
        val pendingAdd = add.copy(batch = "b-w1")
        val groupMove = Op.Move(
            id = "m9", ts = "2026-08-07T13:00:00.001Z",
            url = add.station.url, playlist = add.playlist!!, toGroup = "Jazz",
            batch = "b-w2",
        )
        val survivor = OpsJournal.compact(listOf(pendingAdd, groupMove)).single() as Op.Add
        assertEquals("Jazz", survivor.group)
        assertEquals("b-w1", survivor.batch, "rewrite lost the introducing write's batch")
        assertEquals("2026-08-07T13:00:00.001Z", survivor.ts, "rewrite must take the move's ts")
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
