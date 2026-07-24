package dev.basiliskus.onda.core.ops

import dev.basiliskus.onda.core.m3u.M3uParser
import kotlin.test.*

class ApplyOpsTest {
    private fun fixture(name: String) =
        M3uParser.parsePlaylist(name, javaClass.getResourceAsStream("/playlists/$name.m3u")!!.reader().readText())

    private val playlists = listOf(fixture("stations"), fixture("nts"))

    // nts.m3u fixture facts: group "Live" holds exactly these two urls; "Mixtapes" holds the rest.
    private val live1 = "http://stream-relay-geo.ntslive.net/stream"
    private val live2 = "http://stream-relay-geo.ntslive.net/stream2"

    private fun nts(result: List<dev.basiliskus.onda.core.model.Playlist>) = result.first { it.name == "nts" }

    private fun delete(url: String, playlist: String) = Op.Delete("d-$url", "2026-07-24T10:00:00Z", url, playlist)

    private fun move(url: String, playlist: String, toGroup: String) =
        Op.Move("m-$url", "2026-07-24T10:00:01Z", url, playlist, toGroup)

    private fun add(name: String, url: String, playlist: String?, group: String?, icon: String = "") =
        Op.Add("a-$url", "2026-07-24T10:00:02Z", OpStation(name = name, url = url, icon = icon), playlist, group)

    @Test fun `delete hides the station from its playlist`() {
        val result = applyOps(playlists, listOf(delete(live1, "nts")))
        assertTrue(nts(result).stations.none { it.url == live1 })
        assertEquals(listOf(live2), nts(result).groups.first { it.title == "Live" }.stations.map { it.url })
        assertEquals(playlists[0], result.first { it.name == "stations" }) // other playlist untouched
    }

    @Test fun `delete of last station in group drops the group`() {
        val result = applyOps(playlists, listOf(delete(live1, "nts"), delete(live2, "nts")))
        assertTrue(nts(result).groups.none { it.title == "Live" })
        assertTrue(nts(result).groups.any { it.title == "Mixtapes" })
    }

    @Test fun `move re-groups at target end with pending flag`() {
        val result = applyOps(playlists, listOf(move(live1, "nts", "Mixtapes")))
        assertEquals(listOf(live2), nts(result).groups.first { it.title == "Live" }.stations.map { it.url })
        val moved = nts(result).groups.first { it.title == "Mixtapes" }.stations.last()
        assertEquals(live1, moved.url)
        assertEquals("Mixtapes", moved.group)
        assertTrue(moved.pending)
    }

    @Test fun `move to nonexistent group creates it at playlist end`() {
        val result = applyOps(playlists, listOf(move(live1, "nts", "Archive")))
        val lastGroup = nts(result).groups.last()
        assertEquals("Archive", lastGroup.title)
        assertEquals(listOf(live1), lastGroup.stations.map { it.url })
        assertTrue(lastGroup.stations.single().pending)
    }

    @Test fun `targeted add injects pending-flagged station at group end`() {
        val op = add("NTS Radio 3", "http://nts.example/stream3", "nts", "Live", icon = "http://nts.example/logo.png")
        val injected = applyOps(playlists, listOf(op)).let { nts(it) }.groups.first { it.title == "Live" }.stations.last()
        assertEquals("NTS Radio 3", injected.name)
        assertEquals("http://nts.example/stream3", injected.url)
        assertEquals("Live", injected.group)
        assertEquals("nts", injected.playlist)
        assertEquals("http://nts.example/logo.png", injected.logo)
        assertTrue(injected.pending)
    }

    @Test fun `targeted add creates missing group at playlist end`() {
        val result = applyOps(playlists, listOf(add("New One", "http://new.example/s", "nts", "Archive")))
        val lastGroup = nts(result).groups.last()
        assertEquals("Archive", lastGroup.title)
        assertEquals(listOf("http://new.example/s"), lastGroup.stations.map { it.url })
        assertNull(lastGroup.stations.single().logo) // blank icon maps to null logo
    }

    @Test fun `targeted add of already-present url is a no-op`() {
        assertEquals(playlists, applyOps(playlists, listOf(add("Dup", live1, "nts", "Mixtapes"))))
    }

    @Test fun `null-playlist add is invisible in playlists`() {
        assertEquals(playlists, applyOps(playlists, listOf(add("Quick Star", "http://new.example/s", null, null))))
    }

    @Test fun `delete or move referencing vanished url is a no-op`() {
        val ops = listOf(delete("http://vanished.example/s", "nts"), move("http://vanished.example/s", "nts", "Live"))
        assertEquals(playlists, applyOps(playlists, ops))
    }

    @Test fun `delete then re-add renders the re-added pending station`() {
        val ops = listOf(delete(live1, "nts"), add("NTS Radio 1", live1, "nts", "Mixtapes"))
        val result = applyOps(playlists, ops)
        assertTrue(nts(result).groups.first { it.title == "Live" }.stations.none { it.url == live1 })
        val readded = nts(result).groups.first { it.title == "Mixtapes" }.stations.last()
        assertEquals(live1, readded.url)
        assertTrue(readded.pending)
    }

    @Test fun `no ops returns playlists unchanged (same instances or equal)`() {
        assertSame(playlists, applyOps(playlists, emptyList()))
    }
}
