package twee.highlighting

import com.intellij.lang.ASTNode
import com.intellij.lexer.Lexer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.lang.injection.InjectedLanguageManager
import twee.parser.HarloweLexer
import twee.parser.HarloweTypes as H
import twee.psi.TweeFile
import java.nio.file.Files
import java.nio.file.Path

class HarloweHighlightingTest : BasePlatformTestCase() {
    private data class Token(val start: Int, val end: Int, val type: String, val state: Int)
    private fun tokens(lexer: Lexer, text: String, offset: Int = 0, state: Int = 0): List<Token> {
        lexer.start(text, offset, text.length, state)
        val result = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            assertTrue("Lexer must advance", lexer.tokenEnd > lexer.tokenStart)
            result.add(Token(lexer.tokenStart, lexer.tokenEnd, lexer.tokenType.toString(), lexer.state))
            lexer.advance()
        }
        assertEquals(text.length, result.lastOrNull()?.end ?: offset)
        return result
    }
    private fun shape(tokens: List<Token>) = tokens.map { Triple(it.start, it.end, it.type) }
    private val sample = """
        :: Start [tag]
        Prose isn't a string (ordinary prose).
        (set: ${'$'}x to (a: 1, true, "a
        multiline string", _temp))
        |outer>[{ (print: ${'$'}x) [nested] }]<suffix|
        [[Label->Next]] <!-- (ignore: ${'$'}hidden)
        comment --> <b title="x &amp; y">HTML</b>
        <script>const x = "not (harlowe:)";</script>
        :: Styles [extra stylesheet]
        p { color: red; }
        :: StoryData
        {"format":"Harlowe"}
        :: Next
        (print: "unfinished
        :: Recovered
        [[Start]]
    """.trimIndent()

    fun testEveryTokenRestartAndRestore() {
        for (lexer in listOf(HarloweLexer(), TweeSyntaxHighlighter(project).highlightingLexer)) {
            val full = tokens(lexer, sample)
            for (index in full.indices) {
                val token = full[index]
                assertEquals(shape(full.drop(index)), shape(tokens(lexer, sample, token.start, token.state)))
                lexer.start(sample, token.start, sample.length, token.state)
                val position = lexer.currentPosition
                lexer.advance(); lexer.restore(position)
                assertEquals(token.start, lexer.tokenStart)
                assertEquals(token.type, lexer.tokenType.toString())
            }
        }
    }
    fun testRestartAfterEditMatchesFreshLexing() {
        for ((old, replacement) in listOf("multiline" to "modified", "_temp" to "(max: 2, 3)", "comment -->" to "comment still open", "color: red" to "color: blue")) {
            val lexer = TweeSyntaxHighlighter(project).highlightingLexer
            val before = tokens(lexer, sample)
            val changedAt = sample.indexOf(old)
            val anchor = before.last { it.start <= changedAt }
            val edited = sample.replace(old, replacement)
            val restarted = tokens(lexer, edited, anchor.start, anchor.state)
            val fresh = tokens(TweeSyntaxHighlighter(project).highlightingLexer, edited)
            assertEquals(shape(fresh.filter { it.start >= anchor.start }), shape(restarted))
        }
    }
    fun testEditorIncrementalHighlightingMatchesFreshLexer() {
        myFixture.configureByText("incremental.twee", sample)
        val editor = myFixture.editor as com.intellij.openapi.editor.ex.EditorEx
        val changedAt = editor.document.text.indexOf("_temp")
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            editor.document.replaceString(changedAt, changedAt + 5, "(max: 2, 3)")
        }
        // Header edits must restart before the header classification, not within it.
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            editor.document.replaceString(0, 2, "--")
        }
        val text = editor.document.text
        val iterator = editor.highlighter.createIterator(0)
        val actual = mutableListOf<Triple<Int, Int, String>>()
        while (!iterator.atEnd()) {
            actual.add(Triple(iterator.start, iterator.end, iterator.tokenType.toString()))
            iterator.advance()
        }
        assertEquals(shape(tokens(TweeSyntaxHighlighter(project).highlightingLexer, text)), actual)
    }
    fun testInheritedCategoryRanges() {
        val text = Files.readString(Path.of("tools/characterization/fixtures/edge-cases.twee"))
        val actual = tokens(HarloweLexer(), text)
        val expected = Files.readAllLines(Path.of("src/test/testData/highlighting/inherited-categories.tsv"))
        for (line in expected.filter { !it.startsWith("#") && it.isNotBlank() }) {
            val (a, b, category) = line.split('\t')
            for (offset in a.toInt() until b.toInt()) {
                val token = actual.single { offset in it.start until it.end }
                assertEquals("Inherited category at $offset: ${text.substring(a.toInt(), b.toInt())}", category, token.type)
            }
        }
    }
    fun testProseAndRecoveryDoNotAddErrors() {
        val file = myFixture.configureByText("story.twee", sample) as TweeFile
        assertEmpty(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java))
        assertEquals(listOf("Start", "Styles", "StoryData", "Next", "Recovered"), file.passages.map { it.name })
        val prose = ":: Start\nThis isn't a string (ordinary prose)."
        assertFalse(tokens(HarloweLexer(), prose).any { it.type in listOf("MACRO_OPEN", "STRING") })
        val invalid = myFixture.configureByText("invalid.twee", ":: Start\n(unknown: nonsense, ${'$'}undefined) [[Missing]] <script>const =;</script>")
        assertTrue(myFixture.doHighlighting().none { it.severity.myVal >= com.intellij.lang.annotation.HighlightSeverity.WARNING.myVal })
        val injectionManager = InjectedLanguageManager.getInstance(project)
        PsiTreeUtil.processElements(invalid) { element ->
            assertNull(injectionManager.getInjectedPsiFiles(element)); true
        }
    }
    fun testBodyPsiScopesAndLinks() {
        val text = ":: Start\n(if: true)[|inner>[{(print: (a: 1, 2))}] [[Next]]]"
        val file = myFixture.configureByText("story.tw", text) as TweeFile
        fun types(node: ASTNode): List<com.intellij.psi.tree.IElementType> = listOf(node.elementType) + node.getChildren(null).flatMap { types(it) }
        val kinds = types(file.node)
        assertEquals(3, kinds.count { it == H.MACRO })
        assertEquals(2, kinds.count { it == H.HOOK })
        assertEquals(1, kinds.count { it == H.COLLAPSED })
        assertEquals(1, kinds.count { it == H.LINK })
        assertEquals(text.substringAfter('\n'), file.passages.single().bodyRange.substring(text))
    }
    fun testEmbeddedRegionsUseHostColors() {
        val text = ":: Code [extra script]\nconst value = 42;\n:: Style [stylesheet extra]\np { color: red; }\n:: StoryData\n{\"format\":\"Harlowe\"}\n:: End\n(print: 1)"
        val highlighter = TweeSyntaxHighlighter(project)
        val lexer = highlighter.highlightingLexer
        lexer.start(text)
        val coloredOffsets = mutableSetOf<Int>()
        while (lexer.tokenType != null) {
            if (highlighter.getTokenHighlights(lexer.tokenType!!).isNotEmpty()) coloredOffsets.addAll(lexer.tokenStart until lexer.tokenEnd)
            lexer.advance()
        }
        for (word in listOf("const", "42", "color", "\"format\"")) {
            assertTrue("Embedded host color for $word", text.indexOf(word) in coloredOffsets)
        }
        assertTrue(tokens(HarloweLexer(), text).any { it.type == "MACRO_NAME" && text.substring(it.start, it.end) == "print:" })
    }
    fun testHeadersCommentsHooksAndHtmlAreColored() {
        val text = ":: Start\n|name>[hello]<tail| {collapsed} <!-- comment --> <b id='x &amp; y'>text</b>"
        val actual = tokens(HarloweLexer(), text)
        for (kind in listOf("NAME", "HOOK_NAME", "HOOK_OPEN", "HOOK_CLOSE", "COLLAPSED_OPEN", "COLLAPSED_CLOSE", "COMMENT", "HTML", "ATTRIBUTE", "STRING", "ENTITY"))
            assertTrue("Missing $kind", actual.any { it.type == kind })
    }
}
