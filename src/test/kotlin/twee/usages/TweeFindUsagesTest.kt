package twee.usages

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.PlatformTestUtil
import twee.testing.StoryProjectTestCase
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import twee.psi.TweeFile
import twee.scope.StoryContextService
import twee.scope.StorySettings

class TweeFindUsagesTest : StoryProjectTestCase() {
    override fun setUp() {
        super.setUp()
        project.getService(StorySettings::class.java).loadState(StorySettings.Options(mutableListOf(
            StorySettings.Story("a", "A", mutableListOf("one"), harlowe3WhenMissing = true),
            StorySettings.Story("b", "B", mutableListOf("two"), harlowe3WhenMissing = true)
        ), "a"))
    }
    private fun ready() {
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(project)
        val context = project.getService(StoryContextService::class.java)
        context.requestRefresh()
        PlatformTestUtil.waitWithEventsDispatching("Harlowe context", {
            context.current.storyId == "a" && context.current.format == StoryContextService.Format.HARLOWE_3
        }, 10)
    }
    private fun symbol(file: PsiFile, text: String, occurrence: Int = 0): TweeLogicalSymbol {
        var offset = -1
        repeat(occurrence + 1) { offset = file.text.indexOf(text, offset + 1) }
        assertTrue(offset >= 0)
        return TweeTargetEvaluator().getNamedElement(file.findElementAt(offset)!!) as TweeLogicalSymbol
    }
    private fun usages(target: PsiElement, scope: SearchScope = GlobalSearchScope.projectScope(project)): List<PsiReference> {
        val found = mutableListOf<PsiReference>()
        assertTrue(TweeUsageSearch.process(target, scope, Processor { found.add(it); true }))
        return found
    }
    fun testPassageNamesEscapesDuplicatesAndStoryIsolation() {
        val target = myFixture.addFileToProject("one/target.twee", ":: Snow \\[雪\\]\nbody\n:: Snow \\[雪\\]") as TweeFile
        myFixture.addFileToProject("one/links.tw", ":: Start\n[[Snow \\[雪\\]]](display:'Snow [雪]')")
        myFixture.addFileToProject("two/links.tw", ":: Start\n[[Snow \\[雪\\]]]")
        ready()
        for (passage in target.passages) {
            val refs = ReferencesSearch.search(passage, GlobalSearchScope.projectScope(project)).findAll()
            assertEquals(2, refs.size)
            assertTrue(refs.all { it.isReferenceTo(passage) })
        }
    }
    fun testMacroAliasesIncludeEveryPhysicalCallAndLocalScope() {
        val file = myFixture.addFileToProject("one/a.tw", ":: A\n(go-to:'X')(GO_TO:'X')(-_-_g-o-t-o:'X')")
        myFixture.addFileToProject("one/b.tw", ":: B\n(goto:'X')")
        myFixture.addFileToProject("two/c.tw", ":: C\n(goto:'X')")
        ready()
        val target = symbol(file, "go-to")
        assertEquals("goto", target.name)
        assertEquals(4, usages(target).size)
        assertEquals(3, usages(target, LocalSearchScope(file)).size)
        assertEquals(4, ReferencesSearch.search(target, GlobalSearchScope.projectScope(project)).findAll().size)
    }
    fun testReadWithNoKnownWriteAndTemporaryScopeIdentity() {
        val file = myFixture.addFileToProject("one/a.tw", ":: A\n(print:_unknown)(print:_unknown)[(set:_x to 1)(print:_x)] [(set:_x to 2)(print:_x)]")
        myFixture.addFileToProject("one/b.tw", ":: B\n(print:_unknown)")
        ready()
        assertEquals(2, usages(symbol(file, "_unknown")).size)
        assertEquals(2, usages(symbol(file, "_x")).size)
        assertFalse(symbol(file, "_x").isEquivalentTo(symbol(file, "_x", 2)))
    }
    fun testCallsWritesReadsAndNoDuplicateBindingMetadata() {
        val file = myFixture.addFileToProject("one/a.tw", ":: A\n(set:\$m to (macro:[(print:1)]))(\$m:)(print:\$m)(set:\$m's name to 'x')")
        ready()
        val refs = usages(symbol(file, "\$m"))
        assertEquals(4, refs.size)
        val detector = TweeReadWriteAccessDetector()
        assertEquals(listOf(Access.Write, Access.Read, Access.Read, Access.ReadWrite), refs.map { detector.getReferenceAccess(symbol(file, "\$m"), it) })
    }
    fun testNativeHandlerExactRangesAndConsumerCancellation() {
        val file = myFixture.addFileToProject("one/a.tw", ":: A\n(set:\$x to 1)(print:\$x)")
        ready()
        val target = symbol(file, "\$x")
        val factory = TweeFindUsagesHandlerFactory()
        assertTrue(factory.canFindUsages(target))
        val handler = factory.createFindUsagesHandler(target, false)!!
        val found = mutableListOf<UsageInfo>()
        val options = FindUsagesOptions(project).apply { searchScope = LocalSearchScope(file); isUsages = true }
        assertTrue(handler.processElementUsages(target, Processor { found.add(it); true }, options))
        assertEquals(2, found.size)
        assertTrue(found.all { it.element != null && it.rangeInElement!!.length == 2 })
        var count = 0
        assertFalse(TweeUsageSearch.process(target, options.searchScope, Processor { count++; false }))
        assertEquals(1, count)
    }
    fun testLiveEditsRecomputeTemporaryScopeAndPendingDocumentsCancel() {
        val file = myFixture.addFileToProject("one/a.tw", ":: A\n[(set:_x to 1)(print:_x)]")
        ready()
        val target = symbol(file, "_x")
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(0, "Preamble\n")
            try { usages(target); fail("Uncommitted search must not report a completed empty result") }
            catch (_: ProcessCanceledException) { }
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertTrue(target.isValid)
        assertEquals(2, usages(target).size)
        WriteCommandAction.runWriteCommandAction(project) { document.setText(":: A\nNo variables") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertFalse(target.isValid)
    }
    fun testCaretEntryForUnknownReadAndResolvedCustomBody() {
        val file = myFixture.addFileToProject("one/caret.tw", ":: A\n(print:\$unknown)(set:\$m to (macro:[(print:1)]))(\$m:)")
        ready()
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("\$unknown") + 1)
        val unknown = TargetElementUtil.findTargetElement(myFixture.editor, flags)!!
        assertTrue(TweeFindUsagesHandlerFactory().canFindUsages(unknown))
        myFixture.editor.caretModel.moveToOffset(file.text.lastIndexOf("\$m") + 1)
        val call = TargetElementUtil.findTargetElement(myFixture.editor, flags)!!
        val target = TweeSymbols.target(call)!!
        assertEquals(2, usages(target).size)
    }

}
