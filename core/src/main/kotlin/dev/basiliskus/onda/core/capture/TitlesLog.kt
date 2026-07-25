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

    // Built per call rather than held in a val: a cached formatter would capture the JVM's
    // default locale at class-init time, which would let the pinned English rendering pass by
    // accident on an English machine. Constructing it here keeps TitlesLogTest's foreign-locale
    // rows honest, and a like is a user tap, not a hot path.
    private fun timestamp() = DateTimeFormatter.ofPattern(PATTERN, Locale.ENGLISH)

    // The sole transformation applied to an ICY StreamTitle: a raw CR/LF would split one record
    // into two, and the trailing fragment would still satisfy the consumer's `endswith(" (LIKED)")`
    // and ingest as a truncated title. Framing only — nothing is trimmed, decoded or parsed.
    private fun oneLine(streamTitle: String) = streamTitle.replace('\n', ' ').replace('\r', ' ')
}

/**
 * The current track title, or null when there is nothing likeable: stations broadcast their own
 * name as the title until ICY metadata arrives, and streams without metadata never move off it.
 * Returns the original instance untouched — trimming is the consumer's job, not ours.
 */
fun streamTitleOf(title: String?, station: String?): String? =
    title?.takeIf { it.isNotBlank() && it != station }
