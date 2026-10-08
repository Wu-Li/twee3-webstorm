package twee.tags

import junit.framework.TestCase
import twee.passages.PassageGroups

class TagEditsTest : TestCase() {
    private fun edited(header: String, tag: String, operation: TagEdits.Operation): String {
        val text = StringBuilder(header)
        for (edit in TagEdits.plan(header, tag, operation).sortedByDescending { it.range.startOffset })
            text.replace(edit.range.startOffset, edit.range.endOffset, edit.replacement)
        return text.toString()
    }
    fun testExactInsertionAndPreservation() {
        val before = "::  Snow \\[雪\\]  [ startup\tred  ]  {\"position\":\"1,2\"}\r"
        assertEquals(before.replace("red  ]", "red blue  ]"), edited(before, "blue", TagEdits.Operation.ADD))
        assertEquals(before, edited(before, "red", TagEdits.Operation.ADD))
        assertEquals(":: Snow [blue]  {\"x\":1}\r", edited(":: Snow  {\"x\":1}\r", "blue", TagEdits.Operation.ADD))
    }
    fun testRemoveOnlyMatchingTokensIncludingDuplicates() {
        assertEquals(":: Name [ \tblue   ] {\"x\":1}", edited(":: Name [ red\tblue red  ] {\"x\":1}", "red", TagEdits.Operation.REMOVE))
        assertEquals(":: Name []", edited(":: Name [red]", "red", TagEdits.Operation.REMOVE))
        assertEquals(":: Name [blue]", edited(":: Name [blue]", "red", TagEdits.Operation.REMOVE))
        assertEquals(":: Name [blue  ]", edited(":: Name [  ]", "blue", TagEdits.Operation.ADD))
    }
    fun testInputValidationAndRawEscapedTags() {
        for (bad in listOf("", " ", "a b", "a\nb", "a\tb", "[bad]", "bad{", "bad\\")) assertFalse(bad, TagEdits.valid(bad))
        for (good in listOf("startup", "script", "stylesheet", "雪", "tag\\[x\\]", "two\\\\")) assertTrue(good, TagEdits.valid(good))
        val header = ":: Name [red tag\\[x\\]]"
        val spans = TagEdits.tags(header)
        assertEquals(listOf("red", "tag\\[x\\]"), spans.map { it.text })
        assertTrue(spans.all { it.range.substring(header) == it.text })
    }
    fun testMixedSelectionStates() {
        val state = PassageGroups.selection(listOf(listOf("red", "script"), listOf("red", "blue"), listOf("red")))
        assertEquals(setOf("red"), state.all)
        assertEquals(setOf("blue", "script"), state.some)
        assertTrue(PassageGroups.selection(emptyList()).all.isEmpty())
    }
}
