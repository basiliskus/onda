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

    @Test fun `comma inside a quoted group-title does not truncate the attribute or the name`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="News, Talk",Radio Show
            http://url
        """.trimIndent())
        val s = p.stations.single()
        assertEquals("News, Talk", s.group)
        assertEquals("Radio Show", s.name)
        assertEquals(listOf("News, Talk"), p.groups.map { it.title })
    }

    @Test fun `comma inside group-title combined with comma inside the name`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="News, Talk",Radio Show, Live
            http://url
        """.trimIndent())
        val s = p.stations.single()
        assertEquals("News, Talk", s.group)
        assertEquals("Radio Show, Live", s.name)
    }

    @Test fun `empty group-title lands in Ungrouped`() {
        val p = M3uParser.parsePlaylist("x", "#EXTM3U\n#EXTINF:-1 group-title=\"\",BBC Thing\nhttp://bbc")
        assertEquals("Ungrouped", p.stations.single().group)
    }

    // --- EXTINF quote handling (GitHub #39) -------------------------------------------------
    // Third-party names (radio-browser Discover) reach this parser via the desktop CSV -> M3U
    // export. A name may legitimately contain a `word="value"` substring — scraped HTML
    // fragments are the common real-world source. Such a name must stay a name.

    @Test fun `a name containing an attribute-shaped substring is not dropped`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="Discover",Rock FM <img src="logo.png"> Live
            http://rock
        """.trimIndent())
        val s = p.stations.single()
        assertEquals("""Rock FM <img src="logo.png"> Live""", s.name)
        assertEquals("Discover", s.group)
        assertNull(s.logo)
    }

    @Test fun `a name cannot forge tvg-logo`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="Discover",Rock FM <img tvg-logo="http://attacker.example/beacon.png">, Live
            http://rock
        """.trimIndent())
        val s = p.stations.single()
        assertNull(s.logo, "attacker-chosen logo URL leaked out of the display name")
        assertEquals("""Rock FM <img tvg-logo="http://attacker.example/beacon.png">, Live""", s.name)
        assertEquals("Discover", s.group)
    }

    @Test fun `a name cannot forge group-title`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="Discover",Rock group-title="Hijacked" FM, Live
            http://rock
        """.trimIndent())
        val s = p.stations.single()
        assertEquals("Discover", s.group)
        assertEquals("""Rock group-title="Hijacked" FM, Live""", s.name)
    }

    @Test fun `a name that is entirely an attribute-shaped token stays a name`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="G",tvg-logo="http://attacker.example/x.png"
            http://u
        """.trimIndent())
        val s = p.stations.single()
        assertEquals("""tvg-logo="http://attacker.example/x.png"""", s.name)
        assertEquals("G", s.group)
        assertNull(s.logo)
    }

    @Test fun `a name containing bare quotes with no equals sign is preserved`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="G",Radio "Free" FM
            http://free
        """.trimIndent())
        val s = p.stations.single()
        assertEquals("""Radio "Free" FM""", s.name)
        assertEquals("G", s.group)
    }

    @Test fun `a name containing an equals sign but no quotes is preserved`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="G",Rock = Life FM
            http://eq
        """.trimIndent())
        assertEquals("Rock = Life FM", p.stations.single().name)
    }

    @Test fun `attribute-shaped substring in a name survives alongside a comma-bearing group-title`() {
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://cdn.example/real.png" group-title="News, Talk",Rock FM <img src="logo.png"> Live
            http://both
        """.trimIndent())
        val s = p.stations.single()
        assertEquals("News, Talk", s.group)
        assertEquals("https://cdn.example/real.png", s.logo)
        assertEquals("""Rock FM <img src="logo.png"> Live""", s.name)
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
