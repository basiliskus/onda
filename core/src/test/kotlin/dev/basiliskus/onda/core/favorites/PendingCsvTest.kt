package dev.basiliskus.onda.core.favorites

import kotlin.test.*

class PendingCsvTest {
    private val ntsRow =
        "NTS Radio 1,https://stream-relay-geo.ntslive.net/stream,,https://nts.live/favicon.png,,,,,,," +
            "uuid-1,\"eclectic,london,alternative\",United Kingdom,812"
    private val nts = PendingStation(
        name = "NTS Radio 1", url = "https://stream-relay-geo.ntslive.net/stream",
        icon = "https://nts.live/favicon.png", stationuuid = "uuid-1",
        tags = "eclectic,london,alternative", country = "United Kingdom", votes = 812,
    )

    @Test fun `parse maps the 14-field row to name url icon uuid tags country votes`() {
        assertEquals(listOf(nts), PendingCsv.parse(ntsRow))
    }

    @Test fun `quoted fields with embedded commas and escaped quotes parse back`() {
        val row = "\"The \"\"Wave\"\", FM\",http://wave.fm/live,,,,,,,,,,,,0"
        assertEquals("The \"Wave\", FM", PendingCsv.parse(row).single().name)
    }

    @Test fun `quoted station names starting with hash are not treated as comments`() {
        val row = "\"#Musik.Main\",http://musik.example/main,,,,,,,,,,,,0"
        assertEquals("#Musik.Main", PendingCsv.parse(row).single().name)
    }

    @Test fun `parse skips comments blanks and malformed rows`() {
        val text = "# header\n\n# another comment\nonly-three,fields,here\n$ntsRow\n"
        assertEquals(listOf(nts), PendingCsv.parse(text))
    }
}
