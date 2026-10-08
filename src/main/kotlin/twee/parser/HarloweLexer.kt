package twee.parser

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.lexer.RestartableLexer
import com.intellij.lexer.TokenIterator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.tree.IElementType

/**
 * Tolerant Harlowe scanner. Interned states contain lexical context, never source offsets.
 * State IDs live for this lexer instance, including start()/restore() calls after edits.
 * Embedded lexers are optional: parser PSI uses opaque embedded regions; highlighting uses
 * host lexers without creating injected files or enabling their inspections.
 */
class HarloweLexer(private val enableBody: Boolean = true, private val embeddedLexer: ((String) -> Lexer?)? = null) : LexerBase(), RestartableLexer {
    private data class Context(
        val stack: List<String> = emptyList(), val mode: String = "prose",
        val quote: Char = '\u0000', val embeddedState: Int = 0,
        val inline: Boolean = false, val active: Boolean = false, val afterTag: String = "prose"
    )
    private val states = arrayListOf(Context())
    private val stateIds = hashMapOf(Context() to 0)
    private var context = Context()
    private var after = context
    private var buffer: CharSequence = ""
    private var limit = 0
    private var position = 0
    private var finish = 0
    private var type: IElementType? = null
    private var currentState = 0
    private var headerLexer: TweeLexer? = null
    private var headerEnd = -1
    private var headerOrigin = -1
    private var headerInitialState = 0
    private var nextBody = Context()
    private val embeddedInstances = mutableMapOf<String, Lexer?>()
    private var delegate: Lexer? = null
    private var delegateEnd = -1
    private var delegateMode = ""

    override fun getStartState() = 0
    override fun isRestartableState(state: Int): Boolean {
        val c = states.getOrNull(state) ?: return false
        // Do not promise that arbitrary host-specific states are independently restartable.
        return c.mode != "header" && (c.mode !in listOf("js", "css", "json") || c.embeddedState == 0)
    }
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int, tokenIterator: TokenIterator?) =
        start(buffer, startOffset, endOffset, initialState)
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        require(initialState in states.indices) { "Unknown Harlowe lexer state" }
        this.buffer = buffer; limit = endOffset; position = startOffset
        context = states[initialState]
        headerLexer = null; headerEnd = -1; delegate = null; delegateEnd = -1
        locate()
    }
    private fun state(c: Context) = stateIds.getOrPut(c) { states.add(c); states.lastIndex }
    private fun emit(token: IElementType, end: Int, next: Context = context) {
        type = token; finish = end; after = next
        check(finish > position)
    }
    private fun starts(text: String, at: Int = position, ignoreCase: Boolean = false): Boolean =
        at + text.length <= limit && text.indices.all { buffer[at + it].equals(text[it], ignoreCase) }
    private fun lineEnd(at: Int): Int {
        var i = at
        while (i < limit && buffer[i] != '\n') i++
        return if (i < limit) i + 1 else i
    }
    private fun lineStart(at: Int): Int {
        var i = at
        while (i > 0 && buffer[i - 1] != '\n') i--
        return i
    }
    private fun headerAt(at: Int): TweeHeader? {
        if (!starts("::", at)) return null
        var end = lineEnd(at)
        if (end > at && buffer[end - 1] == '\n') end--
        if (end > at && buffer[end - 1] == '\r') end--
        return TweeHeader.parse(buffer.subSequence(at, end).toString())
    }
    private fun prepareHeader(): Boolean {
        if (headerLexer == null && context.mode != "header" && position > 0 && buffer[position - 1] != '\n') return false
        val start = if (headerLexer == null) lineStart(position) else position
        // Restart within a header reconstructs that line only; body lines do not use this path.
        if (headerLexer == null && (start == position || context.mode == "header")) {
            val header = headerAt(start) ?: return false
            headerOrigin = start
            headerInitialState = state(context)
            headerEnd = lineEnd(start)
            val line = buffer.subSequence(start, headerEnd).toString()
            val tags = header.tags?.substring(line)?.drop(1)?.dropLast(1)?.trim()?.split(Regex("\\s+")) ?: emptyList()
            val mode = when {
                TweeHeader.decode(header.name.substring(line)) == "StoryData" -> "json"
                "script" in tags -> "js"
                "stylesheet" in tags -> "css"
                else -> "prose"
            }
            nextBody = Context(mode = mode, active = true)
            context = Context(mode = "header", active = true)
            headerLexer = TweeLexer().also { it.start(buffer, position, headerEnd, 0) }
            delegate = null
        }
        val lexer = headerLexer ?: return false
        if (position >= headerEnd) { headerLexer = null; context = nextBody; return false }
        currentState = if (position == headerOrigin) headerInitialState else state(context)
        emit(lexer.tokenType!!, lexer.tokenEnd, if (lexer.tokenEnd == headerEnd) nextBody else context)
        return true
    }
    private fun match(regex: Regex): String? = regex.matchAt(buffer, position)?.takeIf { it.range.last < limit }?.value
    private fun expression() = context.stack.lastOrNull() in listOf("macro", "paren")
    private fun push(kind: String) = context.copy(stack = context.stack + kind)
    private fun pop() = context.copy(stack = context.stack.dropLast(1))
    private fun locate() {
        ProgressManager.checkCanceled()
        type = null; finish = position; after = context
        if (position >= limit) { currentState = state(context); return }
        if (prepareHeader()) return
        // Unclosed forms end just before a hook close, allowing the containing hook to close.
        if (buffer[position] == ']') {
            while (context.stack.lastOrNull() in listOf("hook-open", "collapsed-open")) context = pop()
        }
        currentState = state(context)
        if (!context.active || (!enableBody && context.mode != "json")) { emit(TweeTypes.TEXT, lineEnd(position)); return }
        if (context.mode in listOf("js", "css", "json")) { embedded(); return }
        if (context.mode == "html" || context.mode == "html-string") { htmlPart(); return }
        if (context.mode == "comment") { comment(false); return }
        if (context.mode == "string") { string(false); return }
        if (context.mode == "link") {
            when {
                starts("]]" ) && !escaped(position) -> emit(HarloweTypes.LINK_CLOSE, position + 2, context.copy(mode = "prose"))
                starts("->") || starts("<-") -> emit(HarloweTypes.PUNCTUATION, position + 2)
                else -> {
                    var i = position + 1
                    while (i < limit && buffer[i - 1] != '\n' && (escaped(i) || (!starts("]]", i) && !starts("->", i) && !starts("<-", i)))) i++
                    emit(HarloweTypes.LINK_TEXT, i)
                }
            }
            return
        }
        if (starts("<!--")) { comment(true); return }
        if (starts("[[") && !escaped(position) && linkEnds()) { emit(HarloweTypes.LINK_OPEN, position + 2, context.copy(mode = "link")); return }
        match(namedHook)?.let { emit(HarloweTypes.HOOK_NAME, position + it.length); return }
        if (starts("<")) {
            match(html)?.let { tag ->
                val next = when (tag.lowercase()) { "<script" -> "js"; "<style" -> "css"; else -> "prose" }
                emit(HarloweTypes.HTML, position + tag.length, context.copy(mode = "html", afterTag = next)); return
            }
        }
        match(entity)?.let { emit(HarloweTypes.ENTITY, position + it.length); return }
        match(variable)?.let {
            val token = when (it[0]) { '$' -> HarloweTypes.VARIABLE; '_' -> HarloweTypes.TEMP_VARIABLE; else -> HarloweTypes.HOOK_NAME }
            emit(token, position + it.length); return
        }
        if (buffer[position] == '(' && match(macroOpen) != null) {
            emit(HarloweTypes.MACRO_OPEN, position + 1, push("macro")); return
        }
        val c = buffer[position]
        if (expression()) {
            if (c == '(') { emit(HarloweTypes.PAREN_OPEN, position + 1, push("paren")); return }
            if (c == ')') {
                emit(if (context.stack.last() == "macro") HarloweTypes.MACRO_CLOSE else HarloweTypes.PAREN_CLOSE, position + 1, pop()); return
            }
            // The inherited macro-name rule deliberately excludes digits.
            match(macroName)?.let { emit(HarloweTypes.MACRO_NAME, position + it.length); return }
            match(property)?.let { emit(HarloweTypes.PROPERTY, position + it.length); return }
            match(operator)?.let { emit(HarloweTypes.OPERATOR, position + it.length); return }
            if (c == '\'' || c == '"') { string(true); return }
            match(number)?.let { emit(HarloweTypes.NUMBER, position + it.length); return }
            match(boolean)?.let { emit(HarloweTypes.BOOLEAN, position + it.length); return }
            if (c in ",:{}") { emit(HarloweTypes.PUNCTUATION, position + 1); return }
        }
        when {
            c == '[' -> {
                var i = position + 1
                while (i < limit && buffer[i] == '=') i++
                emit(HarloweTypes.HOOK_OPEN, i, push(if (i > position + 1) "hook-open" else "hook")); return
            }
            c == ']' && context.stack.lastOrNull() == "hook" -> {
                emit(HarloweTypes.HOOK_CLOSE, position + 1, pop()); return
            }
            c == '{' -> {
                var i = position + 1
                while (i < limit && buffer[i] == '=') i++
                emit(HarloweTypes.COLLAPSED_OPEN, i, push(if (i > position + 1) "collapsed-open" else "collapsed")); return
            }
            c == '}' && context.stack.lastOrNull() == "collapsed" -> {
                emit(HarloweTypes.COLLAPSED_CLOSE, position + 1, pop()); return
            }
        }
        // Consume prose words/space in runs, stopping before every possible syntax opener.
        var i = position + 1
        while (i < limit && buffer[i - 1] != '\n' && buffer[i] !in "()[]{}<$ _?'\"|&" && !expression()) i++
        emit(TweeTypes.TEXT, i)
    }
    private fun escaped(at: Int): Boolean {
        var i = at - 1
        while (i >= 0 && buffer[i] == '\\') i--
        return (at - i - 1) % 2 != 0
    }
    private fun linkEnds(): Boolean {
        var i = position + 2
        while (i < limit && buffer[i] != '\n') {
            if (buffer[i] == '\\') { i += 2; continue }
            if (buffer[i] == '[') return false
            if (starts("]]", i)) return true
            i++
        }
        return false
    }
    private fun comment(open: Boolean) {
        var i = position + if (open) 4 else 0
        val end = lineEnd(position)
        while (i < end && !starts("-->", i)) i++
        val closed = i < end
        emit(HarloweTypes.COMMENT, if (closed) i + 3 else end, context.copy(mode = if (closed) "prose" else "comment"))
    }
    private fun string(open: Boolean) {
        val quote = if (open) buffer[position] else context.quote
        var i = position + if (open) 1 else 0
        val end = lineEnd(position)
        // Match the inherited immediate-backslash rule, including its even-backslash quirk.
        while (i < end && !(buffer[i] == quote && (i == 0 || buffer[i - 1] != '\\'))) i++
        val closed = i < end
        emit(HarloweTypes.STRING, if (closed) i + 1 else end,
            context.copy(mode = if (closed) "prose" else "string", quote = if (closed) '\u0000' else quote))
    }
    private fun htmlPart() {
        val c = buffer[position]
        if (context.mode == "html-string") {
            if (c == context.quote) {
                emit(HarloweTypes.STRING, position + 1, context.copy(mode = "html", quote = '\u0000')); return
            }
            match(entity)?.let { emit(HarloweTypes.ENTITY, position + it.length); return }
            var i = position + 1
            while (i < limit && buffer[i - 1] != '\n' && buffer[i] != context.quote && buffer[i] != '&') i++
            emit(HarloweTypes.STRING, i); return
        }
        if (starts("/>") || c == '>') {
            val selfClosing = starts("/>")
            val mode = if (selfClosing) "prose" else context.afterTag
            emit(HarloweTypes.HTML, position + if (selfClosing) 2 else 1,
                context.copy(mode = mode, inline = mode != "prose", afterTag = "prose", embeddedState = 0)); return
        }
        if (c == '\'' || c == '"') {
            emit(HarloweTypes.STRING, position + 1, context.copy(mode = "html-string", quote = c)); return
        }
        match(attribute)?.let { emit(HarloweTypes.ATTRIBUTE, position + it.length); return }
        emit(if (c == '=') HarloweTypes.PUNCTUATION else TweeTypes.TEXT, position + 1)
    }
    private fun embedded() {
        val close = if (context.mode == "js") "</script>" else "</style>"
        if (context.inline && starts(close, ignoreCase = true)) {
            emit(HarloweTypes.HTML, position + close.length, context.copy(mode = "prose", inline = false, embeddedState = 0))
            delegate = null; return
        }
        if (delegate == null || delegateMode != context.mode || position >= delegateEnd) {
            delegateEnd = position
            while (delegateEnd < limit) {
                if (delegateEnd > position && buffer[delegateEnd - 1] == '\n' && headerAt(delegateEnd) != null) break
                if (context.inline && starts(close, delegateEnd, true)) break
                if (delegateEnd % 4096 == 0) ProgressManager.checkCanceled()
                delegateEnd++
            }
            delegateMode = context.mode
            delegate = embeddedInstances.getOrPut(context.mode) { embeddedLexer?.invoke(context.mode) }
            delegate?.let { host ->
                // Host highlighting lexers may have layered context not captured by their
                // integer state (notably JavaScript). Replay this region from a clean start.
                host.start(buffer, embeddedRegionStart(), delegateEnd, 0)
                while (host.tokenType != null && host.tokenEnd <= position) {
                    ProgressManager.checkCanceled()
                    host.advance()
                }
            }
        }
        val lexer = delegate
        if (lexer == null || lexer.tokenType == null) {
            emit(HarloweTypes.EMBEDDED, delegateEnd); return
        }
        val token = lexer.tokenType!!
        val end = lexer.tokenEnd
        lexer.advance()
        emit(token, end, context.copy(embeddedState = if (lexer.tokenType == null) 0 else lexer.state))
    }
    private fun embeddedRegionStart(): Int {
        // Passage headers reset lexical context. Reconstruct only the current passage,
        // using the opaque scanner so this cannot recursively instantiate host lexers.
        var anchor = lineStart(position)
        while (anchor > 0 && headerAt(anchor) == null) {
            ProgressManager.checkCanceled()
            anchor = lineStart(anchor - 1)
        }
        val opaque = HarloweLexer(enableBody)
        opaque.start(buffer, anchor, limit, 0)
        while (opaque.tokenType != null) {
            ProgressManager.checkCanceled()
            if (opaque.tokenStart <= position && position < opaque.tokenEnd) {
                check(opaque.tokenType == HarloweTypes.EMBEDDED) { "Expected embedded region at $position" }
                return opaque.tokenStart
            }
            opaque.advance()
        }
        error("Missing embedded region at $position")
    }

    override fun advance() {
        position = finish; context = after
        headerLexer?.advance()
        if (position >= headerEnd) headerLexer = null
        locate()
    }
    override fun getState() = currentState
    override fun getTokenType() = type
    override fun getTokenStart() = position
    override fun getTokenEnd() = finish
    override fun getBufferSequence() = buffer
    override fun getBufferEnd() = limit

    companion object {
        private val macroOpen = Regex("\\((?:[\u0024_]?[a-zA-Z][-\\w]*|-[-\\w]*):")
        private val macroName = Regex("[a-zA-Z_-]+:")
        private val variable = Regex("(?:[\u0024_]|\\?[\u0024_]?)[A-Za-z][A-Za-z0-9_]*\\b")
        private val namedHook = Regex("(?:\\|[a-zA-Z0-9]\\w*[>)])(?=\\[)|(?<=[\\]])[<(][a-zA-Z0-9]\\w*\\|")
        private val property = Regex("of|'s\\b")
        private val operator = Regex("\\b(?:alnum|alphanumeric|an?y?|array|bool(?:ean)?|changer|colou?r|const|command|dm|data|map|type|set|ds|digit|gradient|empty|even|int(?:eger)?|lambda|lowercase|macro|newline|num(?:ber)?|odd|str(?:ing)?|uppercase|whitespace|a|an|its|name|tags|is|to|into|in|when|via|making|each|and|or|not|does|contains|bind|matches|where)\\b|%|\\*|-|\\+|<=|>=|<|>")
        private val number = Regex("\\b(?:0[xX][0-9a-fA-F]+|[0-9]+(?:\\.[0-9]+)?)\\b")
        private val boolean = Regex("\\b(?:true|false)\\b")
        private val entity = Regex("&(?:[a-zA-Z0-9]+|#[0-9]+|#x[0-9a-fA-F]+);")
        private val html = Regex("</?[a-zA-Z][a-zA-Z0-9:-]*\\b")
        private val attribute = Regex("@?[a-zA-Z:-]+")
    }
}
