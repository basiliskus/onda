package dev.basiliskus.onda.core.favorites

import kotlin.test.*

class PendingCsvTest {
    private val nts = PendingStation(
        name = "NTS Radio 1", url = "https://stream-relay-geo.ntslive.net/stream",
        icon = "https://nts.live/favicon.png", stationuuid = "uuid-1",
        tags = "eclectic,london,alternative", country = "United Kingdom", votes = 812,
    )

    @Test fun `serialize then parse round-trips`() {
        val out = PendingCsv.serialize(listOf(nts))
        assertTrue(out.startsWith("# name,url,encoding,icon,"))
        assertEquals(listOf(nts), PendingCsv.parse(out))
    }

    @Test fun `rows have 14 fields with pyradio columns empty except name url icon`() {
        val row = PendingCsv.serialize(listOf(nts)).lines()[1]
        // tags field is quoted (contains commas), so count fields via parse of a tag-free station
        val plain = PendingCsv.serialize(listOf(nts.copy(tags = "t"))).lines()[1]
        assertEquals(14, plain.split(',').size)
        assertTrue(row.contains("\"eclectic,london,alternative\""))
    }

    @Test fun `quotes in names are escaped`() {
        val s = nts.copy(name = "The \"Wave\", FM")
        assertEquals(listOf(s), PendingCsv.parse(PendingCsv.serialize(listOf(s))))
    }

    @Test fun `toggle adds when absent removes when present - by url`() {
        val added = PendingCsv.toggle(emptyList(), nts)
        assertEquals(listOf(nts), added)
        val again = PendingCsv.toggle(added, nts.copy(name = "renamed but same url"))
        assertTrue(again.isEmpty())
    }

    @Test fun `parse skips comments blanks and malformed rows`() {
        val text = "# header\n\n# another comment\nonly-three,fields,here\n" +
            PendingCsv.serialize(listOf(nts)).lines()[1]
        assertEquals(listOf(nts), PendingCsv.parse(text))
    }
}
