package dev.basiliskus.onda.core.m3u

object IndirectionParser {
    private val plsFile = Regex("""^File\d+\s*=\s*(\S+)""", RegexOption.IGNORE_CASE)

    fun looksLikeIndirection(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".pls") || path.endsWith(".m3u")
    }

    fun firstStreamUrl(body: String): String? {
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            plsFile.find(line)?.let { return it.groupValues[1] }
            if (line.startsWith("http://") || line.startsWith("https://")) return line
        }
        return null
    }
}
