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

    @Test fun `a BOM immediately before the first EXTINF is stripped`() {
        // U+FEFF is not Character.isWhitespace, so per-line trimming cannot remove it. In the row
        // above the BOM rides on the #EXTM3U header, which is discarded either way; here the
        // playlist has no header, so the BOM lands on the #EXTINF: line itself and only the
        // explicit strip keeps `startsWith("#EXTINF:")` true. Drop the strip and the entry dies.
        val s = M3uParser.parsePlaylist("x", "﻿#EXTINF:-1 group-title=\"G\",Headerless\nhttp://headerless")
            .stations.single()
        assertEquals("Headerless", s.name)
        assertEquals("G", s.group)
        assertEquals("http://headerless", s.url)
    }

    @Test fun `leading whitespace after the separator comma is not part of the name`() {
        // Hand-edited and third-party playlists write `,` + space; the exporter never does. Only
        // the trim on the display name keeps that space out of the rendered station name.
        val p = M3uParser.parsePlaylist("x", "#EXTM3U\n#EXTINF:-1 group-title=\"G\", \tSpaced Name\nhttp://spaced")
        assertEquals("Spaced Name", p.stations.single().name)
    }

    @Test fun `a non-http line directly after a valid EXTINF is not adopted as a stream`() {
        // The exporter has emitted ftp:// rows. The scheme guard — not a mere null check on the
        // pending entry — is what drops them instead of handing the player an unplayable URL.
        val p = M3uParser.parsePlaylist("x", """
            #EXTM3U
            #EXTINF:-1 group-title="G",Legacy FTP
            ftp://legacy.example/stream.mp3
            #EXTINF:-1 group-title="G",Relative
            ./local/stream.mp3
            #EXTINF:-1 group-title="G",Good
            http://good
        """.trimIndent())
        assertEquals(listOf("http://good"), p.stations.map { it.url })
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
            #EXTINF:-1 group-title="G",
            http://orphan-after-empty-name
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

    @Test fun `unbalanced quote before the separator comma still yields a name`() {
        // No comma exists at quote-depth zero, so only the fallback to the first comma of any
        // kind keeps this entry alive. Delete the fallback and this row fails.
        val p = M3uParser.parsePlaylist("x", "#EXTM3U\n#EXTINF:-1 \"weird,Name\nhttp://weird")
        val s = p.stations.single()
        assertEquals("Name", s.name)
        assertEquals("Ungrouped", s.group)
        assertNull(s.logo)
    }

    @Test fun `an attribute-only line with balanced quotes is dropped, not scavenged`() {
        // The only comma sits inside a quoted value and the quotes balance, so there is no
        // display name — the fallback must NOT fire. Returning firstQuotedComma unconditionally
        // splits inside group-title and invents a station named `Talk"`, which is what the
        // pre-fix parser correctly refused to do. Make the fallback unconditional again and
        // this row fails.
        val p = M3uParser.parsePlaylist("x", "#EXTM3U\n#EXTINF:-1 group-title=\"News, Talk\"\nhttp://x")
        assertEquals(emptyList(), p.stations)
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
