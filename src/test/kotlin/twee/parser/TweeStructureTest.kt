package twee.parser

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import twee.psi.TweeFile

class TweeStructureTest : BasePlatformTestCase() {
    private fun parse(text: String, filename: String = "story.twee"): TweeFile {
        val file = myFixture.configureByText(filename, text) as TweeFile
        // The IDE normalizes document line separators; raw lexer coverage below keeps CRLF input.
        assertEquals(text.replace("\r\n", "\n").replace("\r", "\n"), file.text)
        assertEmpty(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java))
        return file
    }
    fun testExtensionsAndEmptyFiles() {
        for (extension in listOf("tw", "twee")) {
            assertEmpty(parse("", "story.$extension").passages)
            assertEmpty(parse("prose\n::\n:: [", "story.$extension").passages)
        }
    }
    fun testRangesAndEscapedUnicodeNames() {
        val input = "preamble\r\n:: Café \\[east\\] [one script] {\"position\":\"1,2\"}\r\nbody\r\n::End"
        val file = parse(input)
        val text = file.text
        assertEquals(listOf("Café [east]", "End"), file.passages.map { it.name })
        val passage = file.passages.first()
        assertEquals("Café \\[east\\]", passage.rawName)
        assertEquals("[one script]", passage.tagsRange!!.substring(text))
        assertEquals("{\"position\":\"1,2\"}", passage.metadataRange!!.substring(text))
        assertEquals("body\n", passage.bodyRange.substring(text))
        assertEquals(":: Café \\[east\\] [one script] {\"position\":\"1,2\"}", passage.headerRange.substring(text))
        assertEquals(listOf("one", "script"), passage.tags)
        assertEquals(text.indexOf("Café"), passage.textOffset)
        assertTrue(file.passages.last().bodyRange.isEmpty)
    }
    fun testMalformedHeadersStayInBodyAndRecover() {
        val body = ":: Bad [nested[tag]]\n:: Broken {\"x\":}\n:: Unclosed [\n"
        val file = parse(":: Good\n$body:: Later\ntext")
        assertEquals(listOf("Good", "Later"), file.passages.map { it.name })
        assertEquals(body, file.passages.first().bodyRange.substring(file.text))
    }
    fun testJsonAcceptance() {
        val valid = listOf("{}", "{\"x\":[true,false,null,-12.3e+2,{\"u\":\"\\u1234\"}]}")
        val invalid = listOf("{x:1}", "{\"x\":01}", "{\"x\":NaN}", "{\"x\":1,}", "{\"x\":\"\\q\"}", "{\"x\":1} trailing")
        for (metadata in valid) assertEquals(2, parse(":: A $metadata\n:: B").passages.size)
        for (metadata in invalid) assertEquals(listOf("B"), parse(":: A $metadata\n:: B").passages.map { it.name })
    }
    fun testRenamePreservesHeaderAndBody() {
        val file = parse(":: Before [one two] {\"x\":1}\r\nbody")
        WriteCommandAction.runWriteCommandAction(project) { file.passages.single().setName("After [雪]\\path") }
        assertEquals("After [雪]\\path", file.passages.single().name)
        assertEquals(":: After \\[雪\\]\\\\path [one two] {\"x\":1}\nbody", file.text)
    }
    fun testLiveMalformedHeaderThenRepair() {
        val file = parse(":: First\nbody\n:: Next\nend")
        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.text.indexOf("Next") + 4, " [") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertEquals(listOf("First"), file.passages.map { it.name })
        WriteCommandAction.runWriteCommandAction(project) { document.deleteString(document.text.indexOf("Next") + 4, document.text.indexOf("Next") + 6) }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertEquals(listOf("First", "Next"), file.passages.map { it.name })
    }
    fun testStructuralLexerRestartsAtEveryToken() {
        val text = "preamble\n:: 雪 \\[x\\] [tag] {}\r\nbody\n::Bad [\n:: End"
        data class Token(val start: Int, val end: Int, val type: String)
        fun scan(start: Int): List<Token> {
            val lexer = TweeLexer(); lexer.start(text, start, text.length, 0)
            val result = mutableListOf<Token>()
            while (lexer.tokenType != null) {
                assertTrue(lexer.tokenEnd > lexer.tokenStart)
                result.add(Token(lexer.tokenStart, lexer.tokenEnd, lexer.tokenType.toString()))
                lexer.advance()
            }
            return result
        }
        val tokens = scan(0)
        assertEquals(text.length, tokens.sumOf { it.end - it.start })
        for (i in tokens.indices) assertEquals(tokens.drop(i), scan(tokens[i].start))
    }
}
