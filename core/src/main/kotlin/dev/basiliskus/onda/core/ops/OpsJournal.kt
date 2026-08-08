package dev.basiliskus.onda.core.ops

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object OpsJournal {
    data class ParseResult(val ops: List<Op>, val malformed: Int)

    private val json = Json {
        classDiscriminator = "op" // contract uses "op", not the default "type"
        ignoreUnknownKeys = true // forward-compat lenience
        encodeDefaults = true // "playlist":null,"group":null written explicitly, per contract sample
    }

    fun parse(text: String): ParseResult {
        val ops = mutableListOf<Op>()
        var malformed = 0
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            try {
                ops += json.decodeFromString(Op.serializer(), line)
            } catch (_: SerializationException) {
                malformed++
            } catch (_: IllegalArgumentException) {
                malformed++
            }
        }
        return ParseResult(ops, malformed)
    }

    fun serialize(ops: List<Op>): String =
        ops.joinToString("") { json.encodeToString(Op.serializer(), it) + "\n" }

    // Exactly three fraction digits, always — Instant.toString drops a zero fractional
    // second, and mixed-width ISO strings are not byte-order consistent. Fixed width keeps
    // every ts this version stamps both chronologically and lexicographically ordered.
    private val TS = java.time.format.DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT)
        .withZone(java.time.ZoneOffset.UTC)

    /**
     * Stamps the ops entering the journal in one write (GitHub #38): each op whose id is NOT
     * in [priorIds] (the journal's content before this write — new-op detection lives here,
     * not at call sites) gets the write's shared [batch] uuid and a re-minted ts of the
     * write's base plus its stamped index in milliseconds — strictly increasing within the
     * batch, in list order, so the desktop's ts sort needs no stable-tiebreak assumption for
     * ops written together. Prior ops pass through untouched, keeping the batch id of the
     * write that stamped them.
     *
     * The base is [baseTs] floored to sort strictly after every parseable prior ts in [ops]:
     * without the floor, a write landing inside an earlier batch's synthesized +index span —
     * or after the wall clock steps back — would mint ts that a consumer's ts sort applies
     * out of file order. Unparseable prior ts are ignored (the desktop already sorts those
     * last, on file order).
     */
    fun stamp(ops: List<Op>, priorIds: Set<String>, batch: String, baseTs: java.time.Instant): List<Op> {
        val floor = ops.asSequence().filter { it.id in priorIds }
            .mapNotNull { op -> runCatching { java.time.Instant.parse(op.ts) }.getOrNull() }
            .maxOrNull()
        val truncated = baseTs.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        val base = if (floor != null && !truncated.isAfter(floor)) floor.plusMillis(1) else truncated
        var stamped = 0
        return ops.map { op ->
            if (op.id in priorIds) op
            else TS.format(base.plusMillis((stamped++).toLong())).let { ts ->
                when (op) {
                    is Op.Add -> op.copy(ts = ts, batch = batch)
                    is Op.Delete -> op.copy(ts = ts, batch = batch)
                    is Op.Move -> op.copy(ts = ts, batch = batch)
                }
            }
        }
    }

    fun compact(ops: List<Op>): List<Op> {
        val residues = LinkedHashMap<Pair<String, String?>, MutableList<Op>>()
        for (op in ops) {
            val key = when (op) {
                is Op.Add -> op.station.url to op.playlist
                is Op.Delete -> op.url to op.playlist
                is Op.Move -> op.url to op.playlist
            }
            val residue = residues.getOrPut(key) { mutableListOf() }
            when (op) {
                is Op.Add -> when (residue.lastOrNull()) {
                    is Op.Add -> residue[residue.lastIndex] = op // dup adds net to the newest
                    else -> residue += op
                }
                is Op.Delete -> when (residue.lastOrNull()) {
                    // Deleting a not-yet-consumed add = it never happened; a preceding
                    // delete (from delete-then-re-add) is kept as the sole residue.
                    is Op.Add -> residue.removeAt(residue.lastIndex)
                    is Op.Delete -> {}
                    is Op.Move -> residue[residue.lastIndex] = op
                    null -> residue += op
                }
                is Op.Move -> when (val last = residue.lastOrNull()) {
                    // Moving a pending add rewrites the add in place — journal stays minimal.
                    is Op.Add -> residue[residue.lastIndex] = last.copy(group = op.toGroup, ts = op.ts)
                    is Op.Move -> residue[residue.lastIndex] = op
                    else -> residue += op
                }
            }
        }
        return residues.values.flatten()
    }
}
