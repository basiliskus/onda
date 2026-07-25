package dev.basiliskus.onda.core.capture

import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.*

/**
 * The titles-log line format is a contract with `music/scripts/pyradio-to-json.py`, which
 * parses it as `ts_part, " | ", msg` and requires `msg` to end in " (LIKED)". Every row here
 * pins a byte the consumer reads; none of them may be relaxed without the desktop agreeing.
 */
class TitlesLogTest {
    private val defaultLocale = Locale.getDefault()
    private val at = LocalDateTime.of(2026, 7, 23, 21, 8)
    // 2026-07-23 is a Thursday (the pyradio docstring's "(Wed)" example is dated 2025).
    private val line = "Jul 23 (Thu) 21:08 |     Artist - Title (LIKED)"

    @AfterTest fun restoreLocale() = Locale.setDefault(defaultLocale)

    /** What the consumer's `partition(" | ")` hands to its liked-line branch. */
    private fun afterSeparator(rendered: String) = rendered.split(" | ", limit = 2)[1]

    @Test fun `likedLine renders the pinned pyradio line`() {
        assertEquals(line, TitlesLog.likedLine("Artist - Title", at))
    }

    @Test fun `month and weekday stay English under a Turkish default locale`() {
        Locale.setDefault(Locale.forLanguageTag("tr"))
        // Turkish would render "Tem" / "Per" here.
        assertEquals(line, TitlesLog.likedLine("Artist - Title", at))
    }

    @Test fun `digits stay ASCII under a Devanagari-numeral default locale`() {
        Locale.setDefault(Locale.forLanguageTag("hi-IN-u-nu-deva"))
        // The equality below IS the digit assertion: a Devanagari-numeral rendering would give
        // "२१:०८", not "21:08". (A `none { it.isDigit() && it !in '0'..'9' }` check would be
        // vacuous — ofPattern always uses DecimalStyle.STANDARD unless withDecimalStyle is
        // called, and this row is what would fail if it ever were.)
        assertEquals(line, TitlesLog.likedLine("Artist - Title", at))
    }

    @Test fun `timestamp carries no year`() {
        val rendered = TitlesLog.likedLine("Artist - Title", at)
        assertFalse(rendered.contains("2026"), "year leaked into: $rendered")
        assertTrue(
            Regex("""^[A-Z][a-z]{2} \d{2} \([A-Z][a-z]{2}\) \d{2}:\d{2}$""")
                .matches(rendered.substringBefore(" |")),
            "timestamp shape wrong in: $rendered",
        )
    }

    @Test fun `pipe is followed by exactly five spaces`() {
        val rendered = TitlesLog.likedLine("Artist - Title", at)
        assertEquals(5, rendered.substringAfter('|').takeWhile { it == ' ' }.length)
        assertEquals("    Artist - Title (LIKED)", afterSeparator(rendered))
    }

    @Test fun `single-digit day is zero padded`() {
        val rendered = TitlesLog.likedLine("Artist - Title", LocalDateTime.of(2026, 7, 3, 21, 8))
        assertTrue(rendered.startsWith("Jul 03 "), "day not zero padded in: $rendered")
    }

    @Test fun `unicode title passes through unchanged`() {
        val title = "Björk – Jóga ♥ 東京"
        assertEquals("    $title (LIKED)", afterSeparator(TitlesLog.likedLine(title, at)))
    }

    @Test fun `title containing pipes passes through unchanged`() {
        val title = "AC/DC | Live | 1979"
        assertEquals("    $title (LIKED)", afterSeparator(TitlesLog.likedLine(title, at)))
    }

    @Test fun `very long title passes through unchanged`() {
        val title = "x".repeat(4000)
        assertEquals("    $title (LIKED)", afterSeparator(TitlesLog.likedLine(title, at)))
    }

    @Test fun `blank and whitespace-only titles pass through unchanged`() {
        // The formatter is total; refusing to capture an empty title is the app's guard.
        assertEquals("     (LIKED)", afterSeparator(TitlesLog.likedLine("", at)))
        assertEquals("      (LIKED)", afterSeparator(TitlesLog.likedLine(" ", at)))
    }

    @Test fun `untrimmed title keeps its leading and trailing spaces`() {
        val rendered = TitlesLog.likedLine("  Song  ", at)
        assertTrue(rendered.endsWith("  Song   (LIKED)"), "title was trimmed in: $rendered")
        assertTrue(rendered.endsWith(" (LIKED)"))
    }

    @Test fun `every character python splits lines on collapses to a space`() {
        // The invariant the consumer actually cares about: it splits records with Python's
        // str.splitlines(), which treats these ten characters as line boundaries — not just
        // CR/LF. Spelled out here independently of TitlesLog's own set; sharing the constant
        // would make the row a tautology. All ten reach us through ICY StreamTitle, and any
        // survivor lets a crafted title forge extra "Station:"/"(LIKED)" records.
        val boundaries = listOf(
            '\u000A', '\u000B', '\u000C', '\u000D', '\u001C',
            '\u001D', '\u001E', '\u0085', '\u2028', '\u2029',
        )
        val rendered = TitlesLog.likedLine(boundaries.joinToString("") { "A$it" } + "B", at)
        assertTrue(rendered.none { it in boundaries }, "line boundary survived into: $rendered")
        assertEquals("    " + "A ".repeat(boundaries.size) + "B (LIKED)", afterSeparator(rendered))
        // Per-character replacement, so CRLF becomes two spaces — nothing is collapsed or parsed.
        assertEquals("    A B  C (LIKED)", afterSeparator(TitlesLog.likedLine("A\nB\r\nC", at)))
    }

    @Test fun `streamTitleOf rejects absent blank and station-equal titles`() {
        assertNull(streamTitleOf(null, "KEXP"))
        assertNull(streamTitleOf("", "KEXP"))
        assertNull(streamTitleOf("  ", "KEXP"))
        assertNull(streamTitleOf("KEXP", "KEXP"))
        val title = "Artist - Song"
        assertSame(title, streamTitleOf(title, "KEXP"))
        assertSame(title, streamTitleOf(title, null))
    }
}
