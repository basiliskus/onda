package dev.basiliskus.onda.core.m3u

import kotlin.test.*

class M3uParserTest {
    private fun fixture(name: String) =
        javaClass.getResourceAsStream("/playlists/$name.m3u")!!.reader().readText()

    @Test fun `parses a simple entry with group`() {
        val p = M3uParser.parsePlaylist("stations", """
            #EXTM3U
            #EXTINF:-1 group-title="College Radio",KEXP 90.3 Seattle  WA (AAC 160K)
            https://kexp.streamguys1.com/kexp160.aac
        """.trimIndent())
        val s = p.stations.single()
        assertEquals("KEXP 90.3 Seattle  WA (AAC 160K)", s.name)
        assertEquals("https://kexp.streamguys1.com/kexp160.aac", s.url)
        assertEquals("College Radio", s.group)
        assertEquals("stations", s.playlist)
        assertNull(s.logo)
    }

    @Test fun `reads tvg-logo when present and preserves group order`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://a/logo.png" group-title="B",One
            http://one
            #EXTINF:-1 group-title="A",Two
            http://two
            #EXTINF:-1 group-title="B",Three
            http://three
        """.trimIndent())
        assertEquals(listOf("B", "A"), p.groups.map { it.title })
        assertEquals("https://a/logo.png", p.stations.first().logo)
        assertEquals(2, p.groups.first { it.title == "B" }.stations.size)
    }

    @Test fun `tolerates BOM, CRLF, blank lines, and comma in name`() {
        val text = "﻿#EXTM3U\r\n\r\n#EXTINF:-1 group-title=\"G\",Name, with comma\r\nhttp://url\r\n"
        val s = M3uParser.parsePlaylist("x", text).stations.single()
        assertEquals("Name, with comma", s.name)
        assertEquals("http://url", s.url)
    }

    @Test fun `skips malformed entries without failing the file`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="G",No URL follows
            #EXTINF:-1 group-title="G",Good
            http://good
            just-a-stray-line-without-extinf-is-a-bare-url
            #EXTINF:garbage
            http://orphan-after-bad-extinf
        """.trimIndent())
        assertEquals(listOf("http://good"), p.stations.map { it.url })
    }

    @Test fun `missing group lands in Ungrouped`() {
        val p = M3uParser.parsePlaylist("x", "#EXTM3U\n#EXTINF:-1,Bare\nhttp://bare")
        assertEquals("Ungrouped", p.stations.single().group)
    }

    @Test fun `empty group-title lands in Ungrouped`() {
        val p = M3uParser.parsePlaylist("x", "#EXTM3U\n#EXTINF:-1 group-title=\"\",BBC Thing\nhttp://bbc")
        assertEquals("Ungrouped", p.stations.single().group)
    }

    @Test fun `all six real playlists parse with groups and plausible station count`() {
        val names = listOf("stations", "nts", "fip", "bbc", "public-radio", "radio-garden")
        val playlists = names.map { M3uParser.parsePlaylist(it, fixture(it)) }
        val total = playlists.sumOf { it.stations.size }
        assertTrue(total in 150..400, "expected ~205 stations, got $total")
        playlists.forEach { p ->
            assertTrue(p.groups.isNotEmpty(), "${p.name} has no groups")
            p.stations.forEach { s -> assertTrue(s.url.startsWith("http"), "bad url in ${p.name}: ${s.url}") }
        }
    }
}
