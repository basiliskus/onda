package dev.basiliskus.onda.core.capture

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders the one line Onda appends to the synced titles log for a liked track, byte-identical
 * to the `(LIKED)` lines pyradio writes into its own log in the same folder.
 *
 * The format is pinned by the desktop consumer (`music/scripts/pyradio-to-json.py`): it splits
 * on `" | "`, requires the message to end in `" (LIKED)"`, indents liked titles four further
 * spaces, and infers the year from the file's mtime — so the timestamp carries no year.
 */
object TitlesLog {
    private const val PATTERN = "MMM dd (EEE) HH:mm"

    /** Local wall-clock time, matching pyradio; the consumer re-reads it in the desktop's tz. */
    fun likedLine(streamTitle: String, at: LocalDateTime): String =
        "${timestamp().format(at)} |     ${oneLine(streamTitle)} (LIKED)"

    /**
     * The full record for one like: the liked line, preceded by a `>>> Station:` line when
     * [station] names one, joined by the `\n` the log frames records with. The consumer binds
     * the pair by file order — the station line stays in effect until the next station or
     * session line — so writing one per like keeps every like attributable on its own, with no
     * writer-side state to lose across process death or log rotation. Both lines share one
     * timestamp. A blank or absent station yields exactly [likedLine], the pre-#51 shape.
     */
    fun likedRecord(streamTitle: String, station: String?, at: LocalDateTime): String {
        val liked = likedLine(streamTitle, at)
        val name = station?.takeIf { it.isNotBlank() } ?: return liked
        return "${stationLine(name, at)}\n$liked"
    }

    // pyradio's own shape (`>>> ` marker, no four-space indent — the indent is the title
    // marker). The station name is ICY-adjacent untrusted input like the title and gets the
    // same oneLine neutralization: a raw boundary character here forges records exactly as one
    // in a title would.
    private fun stationLine(station: String, at: LocalDateTime): String =
        "${timestamp().format(at)} | >>> Station: ${oneLine(station)}"

    // Built per call rather than held in a val. This is an `object`, so <clinit> is lazy: a
    // cached formatter would be built inside whichever test first touches TitlesLog, after that
    // test's setDefault — baking in that locale for the whole run. If the explicit Locale.ENGLISH
    // below were ever dropped, the foreign-locale rows would then pass or fail purely on JUnit's
    // ordering (observed both ways across two commits of this branch). Per call removes the order
    // dependence, and a like is a user tap, not a hot path. The locale governs MMM/EEE only;
    // ofPattern always uses DecimalStyle.STANDARD, so HH:mm and dd are ASCII regardless.
    private fun timestamp() = DateTimeFormatter.ofPattern(PATTERN, Locale.ENGLISH)

    // Every character Python's str.splitlines() treats as a line boundary — the consumer splits
    // records with exactly that call, so this set, not CR/LF, is what "one line" means here.
    private val LINE_BREAKS = setOf(
        '\n',     // LF
        '\u000B', // VT
        '\u000C', // FF
        '\r',     // CR
        '\u001C', // FS
        '\u001D', // GS
        '\u001E', // RS
        '\u0085', // NEL
        '\u2028', // LINE SEPARATOR
        '\u2029', // PARAGRAPH SEPARATOR
    )

    // The sole transformation applied to an ICY StreamTitle, and the only defence the consumer
    // has: a raw boundary character would split one record into several, each fragment still
    // able to satisfy `endswith(" (LIKED)")` or `startswith("Station: ")` — so a crafted title
    // could forge entries and drop the genuine one. Media3's IcyDecoder matches StreamTitle
    // with DOTALL, so all ten are reachable from the wire. Framing only: replacing a boundary
    // with a space neither trims, decodes nor parses the title.
    private fun oneLine(streamTitle: String) =
        streamTitle.map { if (it in LINE_BREAKS) ' ' else it }.joinToString("")
}

/**
 * The current track title, or null when there is nothing likeable: stations broadcast their own
 * name as the title until ICY metadata arrives, and streams without metadata never move off it.
 * Returns the original instance untouched — trimming is the consumer's job, not ours.
 */
fun streamTitleOf(title: String?, station: String?): String? =
    title?.takeIf { it.isNotBlank() && it != station }
