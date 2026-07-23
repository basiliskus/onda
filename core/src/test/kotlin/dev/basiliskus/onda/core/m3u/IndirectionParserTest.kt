package dev.basiliskus.onda.core.m3u

import kotlin.test.*

class IndirectionParserTest {
    @Test fun `detects pls and m3u but not m3u8 or plain streams`() {
        assertTrue(IndirectionParser.looksLikeIndirection("https://somafm.com/groovesalad.pls"))
        assertTrue(IndirectionParser.looksLikeIndirection("https://kzsu-streams.stanford.edu/kzsu-1-128.mp3.m3u"))
        assertTrue(IndirectionParser.looksLikeIndirection("http://x/y.PLS?sid=1"))
        assertFalse(IndirectionParser.looksLikeIndirection("https://bbc.akamaized.net/pool_904/live.m3u8"))
        assertFalse(IndirectionParser.looksLikeIndirection("https://kexp.streamguys1.com/kexp160.aac"))
        assertFalse(IndirectionParser.looksLikeIndirection("https://netcast.kfjc.org/"))
    }

    @Test fun `parses pls body - somafm style`() {
        val body = """
            [playlist]
            numberofentries=3
            File1=https://ice1.somafm.com/groovesalad-128-aac
            Title1=SomaFM: Groove Salad
            File2=https://ice2.somafm.com/groovesalad-128-aac
            Version=2
        """.trimIndent()
        assertEquals("https://ice1.somafm.com/groovesalad-128-aac", IndirectionParser.firstStreamUrl(body))
    }

    @Test fun `parses m3u body - first non-comment line`() {
        val body = "#EXTM3U\n# comment\nhttps://kzsu-streams.stanford.edu/kzsu-1-128.mp3\n"
        assertEquals("https://kzsu-streams.stanford.edu/kzsu-1-128.mp3", IndirectionParser.firstStreamUrl(body))
    }

    @Test fun `bare url list without extm3u header`() {
        assertEquals("http://relay.example/stream", IndirectionParser.firstStreamUrl("http://relay.example/stream\nhttp://backup.example/stream"))
    }

    @Test fun `returns null when no url found`() {
        assertNull(IndirectionParser.firstStreamUrl("[playlist]\nnumberofentries=0"))
        assertNull(IndirectionParser.firstStreamUrl(""))
    }
}
