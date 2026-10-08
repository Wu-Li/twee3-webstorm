package twee.resolve

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import twee.parser.HarloweTypes
import twee.parser.TweeHeader

/** File-derived facts only. A null name is a dynamic target, never an inferred destination. */
object PassageTargets {
    enum class Kind { LINK, DISPLAY, TRANSITION }
    data class Target(val range: TextRange, val name: String?, val kind: Kind)

    fun extract(node: ASTNode): Target? = when (node.elementType) {
        HarloweTypes.LINK -> bracket(node.text)
        HarloweTypes.MACRO -> macro(node)
        else -> null
    }

    fun bracket(text: String): Target? {
        if (!text.startsWith("[[") || !text.endsWith("]]")) return null
        val content = text.substring(2, text.length - 2)
        val right = mutableListOf<Int>(); val left = mutableListOf<Int>(); val pipes = mutableListOf<Int>()
        var i = 0
        while (i < content.length) {
            if (content[i] == '\\') { i += 2; continue }
            when {
                content.startsWith("->", i) -> right.add(i)
                content.startsWith("<-", i) -> left.add(i)
                content[i] == '|' -> pipes.add(i)
            }
            i++
        }
        val start = when { right.isNotEmpty() -> right.last() + 2; left.isNotEmpty() -> 0; pipes.isNotEmpty() -> pipes.last() + 1; else -> 0 }
        val end = if (right.isEmpty() && left.isNotEmpty()) left.first() else content.length
        val raw = content.substring(start, end)
        return Target(TextRange(start + 2, end + 2),
            if (dynamicMarkup(raw)) null else TweeHeader.decode(raw).trim(), Kind.LINK)
    }

    private fun dynamicMarkup(raw: String): Boolean {
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\\') { i += 2; continue }
            if (dynamic.matchAt(raw, i) != null) return true
            i++
        }
        return false
    }
    private val dynamic = Regex("[\u0024_][A-Za-z][A-Za-z0-9_]*|\\([\u0024_]?[A-Za-z][-\\w]*:")

    private fun macro(node: ASTNode): Target? {
        val children = node.getChildren(null)
        if (children.lastOrNull()?.elementType != HarloweTypes.MACRO_CLOSE) return null
        val nameIndex = children.indexOfFirst { it.elementType == HarloweTypes.MACRO_NAME }
        if (nameIndex < 0) return null
        val name = children[nameIndex].text.dropLast(1).lowercase().replace("-", "").replace("_", "")
        val kind = when (name) {
            "display" -> Kind.DISPLAY
            "goto", "redirect" -> Kind.TRANSITION
            "linkgoto", "linkrevealgoto" -> Kind.LINK
            else -> return null
        }
        // Only immediate commas split arguments: nested macros, parentheses, hooks and
        // commas in string/comment leaves remain part of their containing argument.
        val arguments = mutableListOf<MutableList<ASTNode>>(mutableListOf())
        for (child in children.drop(nameIndex + 1).dropLast(1)) {
            if (child.elementType == HarloweTypes.PUNCTUATION && child.text == ",") arguments.add(mutableListOf())
            else if (child.elementType != HarloweTypes.COMMENT && child.text.isNotBlank()) arguments.last().add(child)
        }
        val index = if (name in setOf("linkgoto", "linkrevealgoto") && arguments.size > 1) 1 else 0
        val argument = arguments.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: return null
        val first = argument.first(); val last = argument.last()
        val range = TextRange(first.startOffset - node.startOffset, last.startOffset + last.textLength - node.startOffset)
        // Multiple adjacent STRING leaves can represent a single multiline literal.
        val raw = range.substring(node.text)
        val quote = raw.firstOrNull()
        if (argument.all { it.elementType == HarloweTypes.STRING } && quote in listOf('\'', '"') && raw.length >= 2 && raw.last() == quote) {
            // Do not mistake two separate string literals for one literal.
            var i = 1
            while (i < raw.lastIndex) {
                if (raw[i] == '\\') { i += 2; continue }
                if (raw[i] == quote) return Target(range, null, kind)
                i++
            }
            return Target(TextRange(range.startOffset + 1, range.endOffset - 1), TweeHeader.decode(raw.substring(1, raw.lastIndex)), kind)
        }
        return Target(range, null, kind)
    }
}
