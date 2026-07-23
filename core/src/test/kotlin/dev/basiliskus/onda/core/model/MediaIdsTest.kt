package dev.basiliskus.onda.core.model

import kotlin.test.*

class MediaIdsTest {
    private val kexp = Station("KEXP 90.3", "https://kexp.streamguys1.com/kexp160.aac", "College Radio", "stations")
    private val playlists = listOf(Playlist("stations", listOf(Group("College Radio", listOf(kexp)))))

    @Test fun `station id is stable and indexable`() {
        val id = MediaIds.station(kexp)
        assertEquals(kexp, MediaIds.stationIndex(playlists)[id])
        assertEquals(id, MediaIds.station(kexp.copy(logo = "x"))) // logo not part of identity
    }

    @Test fun `ids with pipes in names do not collide with structure`() {
        val odd = kexp.copy(name = "we|rd", group = "a|b")
        assertNotNull(MediaIds.stationIndex(listOf(Playlist("p", listOf(Group("a|b", listOf(odd))))))[MediaIds.station(odd)])
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
