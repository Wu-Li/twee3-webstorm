package twee.relations

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import twee.testing.StoryProjectTestCase
import twee.scope.StoryContextService
import twee.scope.StorySettings
import com.intellij.psi.util.PsiTreeUtil
import twee.resolve.PassageTargetPsi
import twee.resolve.CustomMacroReference

class RelationQueryTest : StoryProjectTestCase() {
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
        PlatformTestUtil.waitWithEventsDispatching("Harlowe story context", {
            context.current.storyId == project.getService(StorySettings::class.java).state.selectedStoryId &&
                context.current.format == StoryContextService.Format.HARLOWE_3
        }, 10)
    }
    private fun service() = project.getService(RelationQueryService::class.java)
    fun testDuplicateBindingsAndStoryIsolation() {
        val source = myFixture.addFileToProject("one/a.tw", ":: A\n(set: \$m to (macro: [ [[Target]] ]))(\$m:)")
        myFixture.addFileToProject("one/b.twee", ":: B\n(set: \$m to (macro: [(display:'Target')]))")
        myFixture.addFileToProject("two/c.tw", ":: C\n(set: \$m to (macro: [(print:1)]))(\$m:)")
        ready()
        val calls = service().query("v:\$m")
        assertEquals(RelationQueryService.State.READY, calls.state)
        val call = calls.occurrences.single { it.fact.kind == RelationFact.Kind.CUSTOM_CALL }
        val bindings = service().customBindings(call)
        assertEquals(2, bindings.candidates.size)
        assertTrue(bindings.dynamic)
        assertTrue(bindings.candidates.all { it.element!!.text.startsWith("[") })
        assertEquals(2, service().incomingPassage("Target").occurrences.size)
        assertTrue(calls.occurrences.all { it.symbol.story == "a" })
        val reference = PsiTreeUtil.findChildrenOfType(source, PassageTargetPsi::class.java)
            .flatMap { it.references.toList() }.filterIsInstance<CustomMacroReference>().single()
        assertTrue(reference.isSoft)
        assertEquals(2, reference.multiResolve(false).size)
        assertNull(reference.resolve())
    }
    fun testUnsavedEditsAndScopeChangesInvalidateLiveFacts() {
        val file = myFixture.addFileToProject("one/a.tw", ":: A\n(print: \$old)")
        myFixture.addFileToProject("two/b.tw", ":: B\n(print: \$new)")
        ready(); assertEquals(1, service().query("v:\$old").occurrences.size)
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(":: A\n(set: \$new to 1)(print: \$new)") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertEmpty(service().query("v:\$old").occurrences)
        val fresh = service().query("v:\$new")
        assertEquals(2, fresh.occurrences.size)
        assertEquals(1, service().reads(fresh.occurrences.first().symbol).occurrences.size)
        assertEquals(1, service().writes(fresh.occurrences.first().symbol).occurrences.size)
        val settings = project.getService(StorySettings::class.java)
        val changed = settings.state; changed.selectedStoryId = "b"; settings.loadState(changed)
        ready()
        assertEquals(1, service().query("v:\$new").occurrences.size)
        assertEmpty(service().occurrences(fresh.occurrences.first().symbol).occurrences)
    }
    fun testTemporarySymbolsIncludeFileAndBindingScope() {
        myFixture.addFileToProject("one/a.tw", ":: A\n(print: _unknown)[(set: _local to 1)(print: _local)]")
        myFixture.addFileToProject("one/b.tw", ":: B\n(print: _unknown)")
        ready()
        val unknown = service().query("v:_unknown").occurrences
        assertEquals(2, unknown.size)
        assertFalse(unknown[0].symbol == unknown[1].symbol)
        assertEquals(1, service().reads(unknown[0].symbol).occurrences.size)
        assertEmpty(service().writes(unknown[0].symbol).occurrences)
    }
}
