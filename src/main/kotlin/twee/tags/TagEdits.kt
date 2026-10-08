package twee.tags

import com.intellij.openapi.util.TextRange
import twee.parser.TweeHeader

/** Header-relative, minimal edits. Existing whitespace and tag spelling are never rewritten. */
object TagEdits {
    data class Tag(val text: String, val range: TextRange)
    data class Edit(val range: TextRange, val replacement: String)
    enum class Operation { ADD, REMOVE }

    fun valid(tag: String): Boolean = tag.isNotEmpty() && tag.none { it.isWhitespace() || it.isISOControl() } &&
        TweeHeader.parse(":: Name [$tag]")?.tags?.substring(":: Name [$tag]") == "[$tag]"

    fun tags(header: String): List<Tag> {
        val range = TweeHeader.parse(header)?.tags ?: return emptyList()
        val content = header.substring(range.startOffset + 1, range.endOffset - 1)
        return Regex("\\S+").findAll(content).map {
            Tag(it.value, TextRange(range.startOffset + 1 + it.range.first, range.startOffset + 2 + it.range.last))
        }.toList()
    }

    fun plan(header: String, tag: String, operation: Operation): List<Edit> {
        require(valid(tag)) { "Enter one nonempty tag without whitespace or unescaped brackets/braces." }
        val parsed = requireNotNull(TweeHeader.parse(header)) { "The passage header has changed; refresh and try again." }
        val tags = tags(header)
        return when (operation) {
            Operation.REMOVE -> tags.filter { it.text == tag }.map { Edit(it.range, "") }
            Operation.ADD -> when {
                tags.any { it.text == tag } -> emptyList()
                parsed.tags == null -> listOf(Edit(TextRange(parsed.name.endOffset, parsed.name.endOffset), " [$tag]"))
                else -> {
                    val offset = tags.lastOrNull()?.range?.endOffset ?: (parsed.tags.startOffset + 1)
                    listOf(Edit(TextRange(offset, offset), (if (tags.isEmpty()) "" else " ") + tag))
                }
            }
        }
    }
}
