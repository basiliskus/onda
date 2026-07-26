package dev.basiliskus.onda.core.model

import dev.basiliskus.onda.core.m3u.M3uParser
import kotlin.test.*

class MediaIdsTest {
    private val kexp = Station("KEXP 90.3", "https://kexp.streamguys1.com/kexp160.aac", "College Radio", "stations")
    private val playlists = listOf(Playlist("stations", listOf(Group("College Radio", listOf(kexp)))))

    @Test fun `station id is stable and indexable`() {
        val id = MediaIds.station(kexp)
        assertEquals(kexp, MediaIds.stationIndex(playlists)[id])
        assertEquals(id, MediaIds.station(kexp.copy(logo = "x"))) // logo not part of identity
    }

    @Test fun `station id ignores pending flag`() {
        assertEquals(MediaIds.station(kexp), MediaIds.station(kexp.copy(pending = true)))
    }

    @Test fun `ids with pipes in names do not collide with structure`() {
        val odd = kexp.copy(name = "we|rd", group = "a|b")
        assertNotNull(MediaIds.stationIndex(listOf(Playlist("p", listOf(Group("a|b", listOf(odd))))))[MediaIds.station(odd)])
    }

    @Test fun `resolveStation finds the persisted station id`() {
        assertEquals(kexp, MediaIds.resolveStation(MediaIds.station(kexp), playlists))
    }

    @Test fun `resolveStation returns null for null id`() {
        assertNull(MediaIds.resolveStation(null, playlists))
    }

    @Test fun `resolveStation returns null for stale id`() {
        val removed = kexp.copy(name = "Gone FM", url = "https://gone.example/stream")
        assertNull(MediaIds.resolveStation(MediaIds.station(removed), playlists))
    }

    @Test fun `resolveStation returns null for rb and non-station ids`() {
        assertNull(MediaIds.resolveStation(MediaIds.rb("abc-123"), playlists))
        assertNull(MediaIds.resolveStation("garbage", playlists))
    }

    @Test fun `movedStation follows a station id by url when the id no longer resolves`() {
        val moved = kexp.copy(playlist = "public-radio", group = "Berkeley")
        val after = listOf(Playlist("public-radio", listOf(Group("Berkeley", listOf(moved)))))
        assertNull(MediaIds.resolveStation(MediaIds.station(kexp), after))
        assertEquals(moved, MediaIds.movedStation(MediaIds.station(kexp), kexp.url, after))
    }

    @Test fun `a Discover id is not followed by url even when the url is in a playlist`() {
        assertNull(MediaIds.movedStation(MediaIds.rb("abc-123"), kexp.url, playlists))
        assertNull(MediaIds.movedStation("garbage", kexp.url, playlists))
        assertNull(MediaIds.movedStation(null, kexp.url, playlists))
        assertNull(MediaIds.movedStation(MediaIds.station(kexp), null, playlists))
    }

    @Test fun `a url in two playlists resolves to the first in playlist order`() {
        val twin = kexp.copy(name = "KEXP", playlist = "public-radio", group = "Seattle")
        val order = listOf(
            Playlist("public-radio", listOf(Group("Seattle", listOf(twin)))),
            Playlist("stations", listOf(Group("College Radio", listOf(kexp)))),
        )
        assertEquals(twin, MediaIds.movedStation(MediaIds.station(kexp), kexp.url, order))
        assertEquals(kexp, MediaIds.movedStation(MediaIds.station(kexp), kexp.url, order.reversed()))
    }

    /**
     * Pins the premise the tie-break above exists for. Url sharing across playlists is a
     * property of the real station set, not a contrived case — if it ever stops being true,
     * the tie-break's justification (and the choice to key ids on playlist+group+url rather
     * than url alone) is worth revisiting rather than silently inheriting.
     */
    @Test fun `the committed playlists really do share urls across files`() {
        val names = listOf("stations", "nts", "fip", "bbc", "public-radio", "radio-garden")
        val rows = names.flatMap { name ->
            M3uParser.parsePlaylist(
                name,
                javaClass.getResourceAsStream("/playlists/$name.m3u")!!.reader().readText(),
            ).stations
        }
        val shared = rows.groupBy { it.url }.filterValues { it.size > 1 }
        assertTrue(shared.isNotEmpty(), "expected shared urls across the six playlists")
        assertTrue(
            shared.any { (_, ss) -> ss.map { it.playlist }.distinct().size > 1 },
            "expected at least one url present in two DIFFERENT playlists",
        )
    }

    @Test fun `parse round-trips each node type`() {
        assertEquals(MediaIds.Parsed.Root, MediaIds.parse(MediaIds.ROOT))
        assertEquals(MediaIds.Parsed.PlaylistNode("nts"), MediaIds.parse(MediaIds.playlist("nts")))
        val g = MediaIds.parse(MediaIds.group("nts", "Live")) // group segment is hashed
        assertIs<MediaIds.Parsed.GroupNode>(g)
        assertEquals("nts", g.playlist)
        assertTrue(MediaIds.groupMatches(g, "nts", "Live"))
        assertFalse(MediaIds.groupMatches(g, "nts", "Other"))
        assertIs<MediaIds.Parsed.StationNode>(MediaIds.parse(MediaIds.station(kexp)))
        assertEquals(MediaIds.Parsed.RbNode("abc-123"), MediaIds.parse(MediaIds.rb("abc-123")))
        assertNull(MediaIds.parse("garbage"))
    }
}
