package twee.parser

/** Strict, iterative JSON syntax recognition shared by headers and StoryData. */
internal object TweeJson {
    fun valid(text: String): Boolean = try { Reader(text).read(); true } catch (_: IllegalArgumentException) { false }
    private class Reader(val text: String) {
        var i = 0
        fun whitespace() { while (i < text.length && text[i] in " \t\r\n") i++ }
        fun take(c: Char): Boolean { whitespace(); return if (i < text.length && text[i] == c) { i++; true } else false }
        fun requireChar(c: Char) { require(take(c)) }
        fun read() {
            val pending = ArrayDeque<Char>()
            pending.addLast('v')
            while (pending.isNotEmpty()) {
                com.intellij.openapi.progress.ProgressManager.checkCanceled()
                when (pending.removeLast()) {
                    'v' -> {
                        whitespace(); require(i < text.length)
                        when (text[i]) {
                            '{' -> { i++; pending.addLast('o') }
                            '[' -> { i++; pending.addLast('a') }
                            else -> primitive()
                        }
                    }
                    'o' -> if (!take('}')) {
                        string(); requireChar(':'); pending.addLast('O'); pending.addLast('v')
                    }
                    'O' -> if (take(',')) {
                        string(); requireChar(':'); pending.addLast('O'); pending.addLast('v')
                    } else requireChar('}')
                    'a' -> if (!take(']')) { pending.addLast('A'); pending.addLast('v') }
                    'A' -> if (take(',')) { pending.addLast('A'); pending.addLast('v') } else requireChar(']')
                }
            }
            whitespace(); require(i == text.length)
        }
        fun string() {
            requireChar('"')
            while (i < text.length) {
                val c = text[i++]
                if (c == '"') return
                require(c >= ' ')
                if (c == '\\') {
                    require(i < text.length)
                    when (text[i++]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        'u' -> repeat(4) { require(i < text.length && text[i++] in "0123456789abcdefABCDEF") }
                        else -> throw IllegalArgumentException()
                    }
                }
            }
            throw IllegalArgumentException()
        }
        fun primitive() {
            when (text[i]) {
                '"' -> string()
                't', 'f', 'n' -> {
                    val word = when (text[i]) { 't' -> "true"; 'f' -> "false"; else -> "null" }
                    require(text.startsWith(word, i)); i += word.length
                }
                else -> {
                    if (text[i] == '-') i++
                    require(i < text.length)
                    if (text[i] == '0') i++ else {
                        require(text[i] in '1'..'9'); while (i < text.length && text[i] in '0'..'9') i++
                    }
                    if (i < text.length && text[i] == '.') {
                        i++; val start = i
                        while (i < text.length && text[i] in '0'..'9') i++
                        require(i > start)
                    }
                    if (i < text.length && text[i] in "eE") {
                        i++; if (i < text.length && text[i] in "+-") i++
                        val start = i
                        while (i < text.length && text[i] in '0'..'9') i++
                        require(i > start)
                    }
                }
            }
        }
    }
}
