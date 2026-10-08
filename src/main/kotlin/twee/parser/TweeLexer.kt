package twee.parser

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/** Line-local structural lexer. Restart reconstructs only the containing line. */
class TweeLexer : LexerBase() {
    private data class Token(val type: IElementType, val start: Int, val end: Int)
    private var buffer: CharSequence = ""
    private var end = 0
    private var tokens = emptyList<Token>()
    private var index = 0
    private var position = 0
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer; end = endOffset; position = startOffset
        loadLine()
    }
    private fun loadLine() {
        index = 0
        if (position >= end) { tokens = emptyList(); return }
        var start = position
        while (start > 0 && buffer[start - 1] != '\n') start--
        var finish = position
        while (finish < end && buffer[finish] != '\n') finish++
        val contentEnd = if (finish > start && buffer[finish - 1] == '\r') finish - 1 else finish
        val lineEnd = if (finish < end) finish + 1 else finish
        val header = TweeHeader.parse(buffer.subSequence(start, contentEnd).toString())
        val all = mutableListOf<Token>()
        fun add(type: IElementType, a: Int, b: Int) { if (b > a) all.add(Token(type, a, b)) }
        if (header == null) add(TweeTypes.TEXT, start, lineEnd) else {
            add(TweeTypes.MARKER, start, start + 2)
            var cursor = start + 2
            for ((type, range) in listOf(TweeTypes.NAME to header.name, TweeTypes.TAGS to header.tags, TweeTypes.METADATA to header.metadata)) {
                if (range == null) continue
                add(TweeTypes.HEADER_SPACE, cursor, start + range.startOffset)
                add(type, start + range.startOffset, start + range.endOffset)
                cursor = start + range.endOffset
            }
            add(TweeTypes.HEADER_SPACE, cursor, contentEnd)
            add(TweeTypes.NEWLINE, contentEnd, lineEnd)
        }
        tokens = all.filter { it.end > position }.map { it.copy(start = maxOf(it.start, position)) }
    }
    override fun getState() = 0
    override fun getTokenType(): IElementType? = tokens.getOrNull(index)?.type
    override fun getTokenStart() = tokens.getOrNull(index)?.start ?: end
    override fun getTokenEnd() = tokens.getOrNull(index)?.end ?: end
    override fun advance() {
        position = tokenEnd; index++
        if (index >= tokens.size) loadLine()
    }
    override fun getBufferSequence() = buffer
    override fun getBufferEnd() = end
}
