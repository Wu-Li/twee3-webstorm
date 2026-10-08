package twee.index

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import twee.parser.TweeTypes
import twee.psi.TweeFile
import twee.scope.StorySettings

class PassageIndexTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.getService(StorySettings::class.java).loadState(StorySettings.Options(mutableListOf(
            StorySettings.Story("a", "A", mutableListOf("one")), StorySettings.Story("b", "B", mutableListOf("two"))
        ), "a"))
    }
    private fun query(kind: PassageQueryService.Kind, key: String = "", story: String? = null): PassageQueryService.Result {
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(project)
        val result = ReadAction.compute<PassageQueryService.Result, RuntimeException> {
            project.getService(PassageQueryService::class.java).query(PassageQueryService.Request(kind, key, story))
        }
        assertEquals(PassageQueryService.State.READY, result.state)
        return result
    }
    fun testNamesDuplicatesCaseAndStoryIsolation() {
        myFixture.addFileToProject("one/first.tw", ":: Shared\nfirst\n:: Shared\nsecond\n:: shared")
        myFixture.addFileToProject("two/second.twee", ":: Shared")
        assertEquals(2, query(PassageQueryService.Kind.NAME, "Shared").passages.size)
        assertEquals(1, query(PassageQueryService.Kind.NAME, "shared").passages.size)
        val other = query(PassageQueryService.Kind.NAME, "Shared", "b").passages.single()
        assertEquals("b", other.storyId)
        assertTrue(other.pointer.element!!.containingFile.virtualFile.path.endsWith("two/second.twee"))
    }
    fun testEscapedNamesAndTags() {
        myFixture.addFileToProject("one/names.tw", ":: Snow \\[雪\\] [red red script]\nbody\n:: Plain [blue]")
        val passage = query(PassageQueryService.Kind.NAME, "Snow [雪]").passages.single()
        assertEquals("Snow \\[雪\\]", passage.pointer.element!!.rawName)
        assertEquals(1, query(PassageQueryService.Kind.TAG, "red").passages.size)
        assertEquals(2, query(PassageQueryService.Kind.ALL).passages.size)
    }
    fun testCommittedUnsavedRenameAndTagChange() {
        val file = myFixture.addFileToProject("one/change.tw", ":: Before [old]")
        assertEquals(1, query(PassageQueryService.Kind.NAME, "Before").passages.size)
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(":: After [new]\n:: Added") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertEmpty(query(PassageQueryService.Kind.NAME, "Before").passages)
        assertEquals(1, query(PassageQueryService.Kind.NAME, "After").passages.size)
        assertEquals(1, query(PassageQueryService.Kind.NAME, "Added").passages.size)
        assertEmpty(query(PassageQueryService.Kind.TAG, "old").passages)
        assertEquals(1, query(PassageQueryService.Kind.TAG, "new").passages.size)
    }
    fun testDeletionAndMoveBetweenStories() {
        val file = myFixture.addFileToProject("one/move.tw", ":: Moving").virtualFile
        val destination = myFixture.addFileToProject("two/anchor.tw", ":: Anchor").virtualFile.parent
        assertEquals(1, query(PassageQueryService.Kind.NAME, "Moving").passages.size)
        WriteCommandAction.runWriteCommandAction(project) { file.move(this, destination) }
        assertEmpty(query(PassageQueryService.Kind.NAME, "Moving").passages)
        assertEquals(1, query(PassageQueryService.Kind.NAME, "Moving", "b").passages.size)
        WriteCommandAction.runWriteCommandAction(project) { file.delete(this) }
        assertEmpty(query(PassageQueryService.Kind.NAME, "Moving", "b").passages)
    }
    fun testScopeChangesWithoutReindexingFileFacts() {
        myFixture.addFileToProject("one/start.tw", ":: Start [intro]")
        myFixture.addFileToProject("two/start.tw", ":: Start [intro]")
        assertEquals("a", query(PassageQueryService.Kind.TAG, "intro").passages.single().storyId)
        val settings = project.getService(StorySettings::class.java)
        val changed = settings.state; changed.selectedStoryId = "b"; settings.loadState(changed)
        assertEquals("b", query(PassageQueryService.Kind.TAG, "intro").passages.single().storyId)
        changed.stories.last().exclusions.add("two/start.tw"); settings.loadState(changed)
        assertEmpty(query(PassageQueryService.Kind.ALL).passages)
    }
    fun testStubFactsWithoutAstAndMalformedHeaderExclusion() {
        val stub = TweePassageStub(TweeFileStub(null), "A\\[B\\]", "A[B]", listOf("tag"))
        val psi = stub.psi
        assertEquals("A[B]", psi.name); assertEquals("A\\[B\\]", psi.rawName); assertEquals(listOf("tag"), psi.tags)
        assertSame(stub, psi.stub)
        val file = myFixture.configureByText("stub.tw", ":: Good [tag]\n:: Bad {nope}\n:: Later") as TweeFile
        val tree = TweeTypes.FILE.builder.buildStubTree(file)
        val passages = tree.childrenStubs.filterIsInstance<TweePassageStub>()
        assertEquals(listOf("Good", "Later"), passages.map { it.passageName })
        assertEquals(listOf("tag"), passages.first().tags)
    }
    fun testUncommittedDocumentsAreExplicitlyPending() {
        val file = myFixture.addFileToProject("one/pending.tw", ":: Before")
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(document.textLength, "\n:: After")
            val result = project.getService(PassageQueryService::class.java).query(PassageQueryService.Request(PassageQueryService.Kind.ALL))
            assertEquals(PassageQueryService.State.DOCUMENTS_UNCOMMITTED, result.state)
        }
    }
}
