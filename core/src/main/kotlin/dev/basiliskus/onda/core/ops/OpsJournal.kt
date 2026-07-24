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

    fun removeOp(ops: List<Op>, id: String): List<Op> = ops.filterNot { it.id == id }

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
