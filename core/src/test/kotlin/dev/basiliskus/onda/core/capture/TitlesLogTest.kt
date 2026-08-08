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

    /**
     * A second-locale copy of the Turkish row above: it fails on the same mechanism (the month
     * and weekday names, which would render "जुल॰" / "गुरु" here), so a red here is not
     * independent evidence from a red there.
     *
     * `Locale.ENGLISH` does **not** force ASCII digits — `ofPattern` always uses
     * `DecimalStyle.STANDARD` whatever locale it is handed, so the digits are ASCII with or
     * without it. The row is kept for the one digit regression that is real: adding
     * `withDecimalStyle(DecimalStyle.ofDefaultLocale())` renders "२१:०८" under this locale and
     * nothing else in the suite would notice.
     */
    @Test fun `a Devanagari-numeral default locale changes neither names nor digits`() {
        Locale.setDefault(Locale.forLanguageTag("hi-IN-u-nu-deva"))
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

    // Real pyradio station payload, internal double space included — the desktop writes the
    // pattern `>>> Station: <name>` with no four-space indent (the indent is the title marker).
    private val kexp = "KEXP 90.3 Seattle  WA (AAC 160K)"
    private val stationLine = "Jul 23 (Thu) 21:08 | >>> Station: $kexp"

    @Test fun `likedRecord with a station renders the pinned pair, station line first`() {
        assertEquals("$stationLine\n$line", TitlesLog.likedRecord("Artist - Title", kexp, at))
    }

    @Test fun `absent and blank stations yield exactly the liked line`() {
        assertEquals(line, TitlesLog.likedRecord("Artist - Title", null, at))
        assertEquals(line, TitlesLog.likedRecord("Artist - Title", "", at))
        assertEquals(line, TitlesLog.likedRecord("Artist - Title", "   ", at))
    }

    @Test fun `station line stays English under a Turkish default locale`() {
        Locale.setDefault(Locale.forLanguageTag("tr"))
        assertEquals("$stationLine\n$line", TitlesLog.likedRecord("Artist - Title", kexp, at))
    }

    @Test fun `untrimmed station name is written raw`() {
        val record = TitlesLog.likedRecord("Artist - Title", "  KEXP  ", at)
        assertEquals(">>> Station:   KEXP  ", afterSeparator(record.substringBefore("\n")))
    }

    @Test fun `every line boundary in a station name collapses to a space`() {
        // Same independent spelling of the ten as the title row above, for the same reason.
        val boundaries = listOf(
            '\u000A', '\u000B', '\u000C', '\u000D', '\u001C',
            '\u001D', '\u001E', '\u0085', '\u2028', '\u2029',
        )
        val hostile = boundaries.joinToString("") { "A$it" } + "B"
        val station = TitlesLog.likedRecord("Artist - Title", hostile, at).substringBefore("\n")
        assertTrue(station.none { it in boundaries }, "line boundary survived into: $station")
        assertEquals(">>> Station: " + "A ".repeat(boundaries.size) + "B", afterSeparator(station))
    }

    @Test fun `a station name cannot forge a liked record`() {
        // A raw newline would emit the forged text as its own byte-identical line, which no
        // parser can then tell from a genuine record. Neutralized, the forged text stays
        // inside the station line — where the consumer's prefix check ("Station: " before the
        // "(LIKED)" suffix) classifies the whole line as a station, never as a like.
        val hostile = "KEXP\nJul 23 (Thu) 21:07 |     Evil - Song (LIKED)"
        assertEquals(
            "Jul 23 (Thu) 21:08 | >>> Station: KEXP Jul 23 (Thu) 21:07 |     Evil - Song (LIKED)\n$line",
            TitlesLog.likedRecord("Artist - Title", hostile, at),
        )
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
