package twee.tags

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import twee.testing.StoryProjectTestCase
import twee.index.PassageQueryService
import twee.passages.PassageGroups
import twee.psi.TweeFile
import twee.scope.StorySettings

class PassageTagTest : StoryProjectTestCase() {
    override fun setUp() {
        super.setUp()
        project.getService(StorySettings::class.java).loadState(StorySettings.Options(mutableListOf(
            StorySettings.Story("story", "Story", mutableListOf("one"))
        ), "story"))
    }
    private fun pointers(vararg files: TweeFile) = files.flatMap { it.passages }.map {
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it)
    }
    private fun service() = project.getService(PassageTagService::class.java)
    private fun all(): PassageQueryService.Result {
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(project)
        return project.getService(PassageQueryService::class.java).query(PassageQueryService.Request(PassageQueryService.Kind.ALL))
    }
    fun testBatchDescendingOffsetsAndBodyMetadataPreservation() {
        val original = ":: Snow \\[雪\\] [red] {\"x\":1}\nbody [red]\n:: Last\nunchanged body"
        val file = myFixture.addFileToProject("one/batch.tw", original) as TweeFile
        val refs = pointers(file)
        assertEquals(2, service().edit(refs + refs, "script", TagEdits.Operation.ADD))
        assertEquals(original.replace("[red] {", "[red script] {").replace(":: Last", ":: Last [script]"), file.text)
        assertEquals(0, service().edit(refs, "script", TagEdits.Operation.ADD))
        assertEquals(2, service().edit(refs, "script", TagEdits.Operation.REMOVE))
        assertEquals(original.replace("[red] {", "[red ] {").replace(":: Last", ":: Last []"), file.text)
    }
    fun testMultiFileUndoRedo() {
        val first = myFixture.addFileToProject("one/a.tw", ":: A\nbody") as TweeFile
        val second = myFixture.addFileToProject("one/b.tw", ":: B [old]\nbody") as TweeFile
        service().edit(pointers(first, second), "new", TagEdits.Operation.ADD)
        val undo = UndoManager.getInstance(project)
        assertTrue(undo.isUndoAvailable(null)); undo.undo(null)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(":: A\nbody", first.text); assertEquals(":: B [old]\nbody", second.text)
        assertTrue(undo.isRedoAvailable(null)); undo.redo(null)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(":: A [new]\nbody", first.text); assertEquals(":: B [old new]\nbody", second.text)
    }
    fun testUnsavedChangesGroupingAndRefresh() {
        val file = myFixture.addFileToProject("one/tags.twee", ":: Before") as TweeFile
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(":: After [red red]\nbody\n:: Plain\n:: Named [Untagged]") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val refs = pointers(file)
        assertEquals(3, service().edit(refs, "blue", TagEdits.Operation.ADD))
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        val result = all()
        assertEquals(PassageQueryService.State.READY, result.state)
        val groups = PassageGroups.group(result.passages)
        assertEquals(3, groups["blue"]!!.size); assertEquals(1, groups["red"]!!.size)
        service().edit(listOf(refs[1]), "blue", TagEdits.Operation.REMOVE)
        val refreshed = PassageGroups.group(all().passages)
        assertEquals("Plain", refreshed[null]!!.single().name)
        assertEquals("Named", refreshed["Untagged"]!!.single().name)
    }
    fun testRejectInvalidOrOutOfStoryBatchWithoutPartialWrites() {
        val first = myFixture.addFileToProject("one/a.tw", ":: A") as TweeFile
        val outside = myFixture.addFileToProject("two/b.tw", ":: B") as TweeFile
        try { service().edit(pointers(first, outside), "red", TagEdits.Operation.ADD); fail("Out-of-story edit accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(":: A", first.text); assertEquals(":: B", outside.text)
        try { service().edit(pointers(first), "bad tag", TagEdits.Operation.ADD); fail("Invalid tag accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(":: A", first.text)
    }
}
