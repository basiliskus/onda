package dev.basiliskus.onda.core.favorites

data class PendingStation(
    val name: String,
    val url: String,
    val icon: String = "",
    val stationuuid: String = "",
    val tags: String = "",
    val country: String = "",
    val votes: Int = 0,
)

/**
 * Parse-only reader for the legacy pyradio pending.csv format; sole remaining consumer is
 * the one-time migration to the ops journal (writing pending.csv is no longer a concept).
 */
object PendingCsv {
    fun parse(text: String): List<PendingStation> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val f = splitCsv(line)
                if (f.size != 14) return@mapNotNull null
                PendingStation(
                    name = f[0], url = f[1], icon = f[3],
                    stationuuid = f[10], tags = f[11], country = f[12],
                    votes = f[13].toIntOrNull() ?: 0,
                )
            }
            .toList()

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
