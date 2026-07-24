package dev.basiliskus.onda.core.ops

import kotlin.test.*

class LegacyPendingMigrationTest {
    private val header =
        "# name,url,encoding,icon,profile,buffering,force-http,volume,referer,player,stationuuid,tags,country,votes"
    private val ntsRow =
        "NTS Radio 1,https://stream-relay-geo.ntslive.net/stream,,https://nts.live/favicon.png,,,,,,," +
            "uuid-1,\"eclectic,london,alternative\",United Kingdom,812"

    @Test fun `rows map to null-playlist add ops preserving name url icon uuid tags country votes`() {
        var n = 0
        val ops = LegacyPendingMigration.toOps("$header\n$ntsRow\n", ids = { "id-${n++}" }, ts = "2026-07-24T00:00:00Z")
        assertEquals(
            listOf(
                Op.Add(
                    id = "id-0", ts = "2026-07-24T00:00:00Z",
                    station = OpStation(
                        name = "NTS Radio 1", url = "https://stream-relay-geo.ntslive.net/stream",
                        icon = "https://nts.live/favicon.png", stationuuid = "uuid-1",
                        tags = "eclectic,london,alternative", country = "United Kingdom", votes = 812,
                    ),
                    playlist = null, group = null,
                ),
            ),
            ops,
        )
    }

    @Test fun `empty or header-only csv maps to no ops`() {
        assertTrue(LegacyPendingMigration.toOps("", ids = { fail("no ids expected") }, ts = "t").isEmpty())
        assertTrue(LegacyPendingMigration.toOps("$header\n", ids = { fail("no ids expected") }, ts = "t").isEmpty())
    }

    @Test fun `malformed rows are skipped`() {
        val ops = LegacyPendingMigration.toOps(
            "$header\nonly-three,fields,here\n$ntsRow\n", ids = { "id" }, ts = "t",
        )
        assertEquals(1, ops.size)
        assertEquals("NTS Radio 1", (ops.single()).station.name)
    }
}
