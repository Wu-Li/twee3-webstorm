package twee.parser

import com.intellij.openapi.util.TextRange

/** Header recognition adapted from src/parse-text.ts (Cyrus Firheir, MIT). */
data class TweeHeader(val name: TextRange, val tags: TextRange?, val metadata: TextRange?) {
    companion object {
        private val header = Regex("(^::\\s*)(.*?)(\\[.*?\\]\\s*)?(\\{.*?\\}\\s*)?\\r?$")
        private val escaped = Regex("\\\\.")
        private val reserved = Regex("[\\[\\]{}]")
        fun decode(raw: String) = escaped.replace(raw) { it.value.substring(1) }
        fun encode(name: String): String = buildString {
            for (c in name) { if (c in "\\[]{}") append('\\'); append(c) }
        }
        fun parse(line: String): TweeHeader? {
            val masked = escaped.replace(line, "ec")
            val match = header.matchEntire(masked) ?: return null
            fun range(group: Int): TextRange? {
                val g = match.groups[group] ?: return null
                var start = g.range.first
                var end = g.range.last + 1
                while (start < end && line[start].isWhitespace()) start++
                while (end > start && line[end - 1].isWhitespace()) end--
                return TextRange(start, end)
            }
            val name = range(2) ?: return null
            if (name.isEmpty || reserved.containsMatchIn(escaped.replace(name.substring(line), "ec"))) return null
            val tags = range(3)
            if (tags != null && reserved.containsMatchIn(escaped.replace(tags.substring(line).drop(1).dropLast(1), "ec"))) return null
            val metadata = range(4)
            if (metadata != null && !TweeJson.valid(metadata.substring(line))) return null
            return TweeHeader(name, tags, metadata)
        }
    }
}
