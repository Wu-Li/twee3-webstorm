package twee.integration

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import twee.testing.StoryProjectTestCase
import twee.hierarchy.TweeHierarchyModel
import twee.index.PassageQueryService
import twee.psi.TweeFile
import twee.relations.RelationFact
import twee.relations.RelationQueryService
import twee.resolve.PassageReference
import twee.resolve.PassageTargetPsi
import twee.scope.StoryContextService
import twee.scope.StorySettings
import twee.tags.PassageTagService
import twee.tags.TagEdits
import java.nio.file.Files
import java.nio.file.Path

/** Integration across live scope, references, static tracing, tagging and committed unsaved edits. */
class StoryWorkflowTest : StoryProjectTestCase() {
    fun testSharedSmokeStoryNavigationTracingTagsAndEdit() {
        project.getService(StorySettings::class.java).loadState(StorySettings.Options(mutableListOf(
            StorySettings.Story("smoke", "Smoke", mutableListOf("story")),
            StorySettings.Story("other", "Other", mutableListOf("other"))
        ), "smoke"))
        val fixture = Path.of("src/test/testData/run/story with spaces/source")
        val files = Files.list(fixture).use { paths -> paths.sorted().map { path ->
            myFixture.addFileToProject("story/${path.fileName}", Files.readString(path))
        }.toList() }
        myFixture.addFileToProject("other/duplicate.tw", ":: Across files\nWrong story")
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        val context = project.getService(StoryContextService::class.java)
        context.requestRefresh()
        PlatformTestUtil.waitWithEventsDispatching("Smoke StoryData", {
            context.current.storyId == "smoke" && context.current.format == StoryContextService.Format.HARLOWE_3
        }, 10)
        val query = project.getService(PassageQueryService::class.java)
        val start = files.single { it.name == "start.twee" } as TweeFile
        val target = query.query(PassageQueryService.Request(PassageQueryService.Kind.NAME, "Across files"))
        assertEquals(PassageQueryService.State.READY, target.state)
        assertEquals(1, target.passages.size)
        assertEquals("ending.tw", target.passages.single().pointer.element!!.containingFile.name)
        val reference = PsiTreeUtil.findChildrenOfType(start, PassageTargetPsi::class.java)
            .flatMap { it.references.toList() }.filterIsInstance<PassageReference>().single()
        assertEquals(target.passages.single().pointer.element, reference.resolve())
        val relations = project.getService(RelationQueryService::class.java)
        val calls = relations.query("v:\$greet").occurrences.filter { it.fact.kind == RelationFact.Kind.CUSTOM_CALL }
        assertEquals(1, calls.size)
        assertEquals("startup.tw", relations.customBindings(calls.single()).candidates.single().element!!.containingFile.name)
        val variable = relations.query("v:\$visits")
        assertTrue(variable.occurrences.any { it.fact.reads }); assertTrue(variable.occurrences.any { it.fact.writes })
        val model = TweeHierarchyModel(project)
        val arrival = start.passages.single { it.name == "Arrival" }
        val outgoing = model.children(model.at(arrival)!!, TweeHierarchyModel.View.OUTGOING)
        assertTrue(outgoing.rows.any { it.target?.element == target.passages.single().pointer.element })
        val body = target.passages.single().pointer.element!!.let { it.bodyRange.substring(it.containingFile.text) }
        project.getService(PassageTagService::class.java).edit(target.passages.map { it.pointer }, "reviewed", TagEdits.Operation.ADD)
        val tagged = query.query(PassageQueryService.Request(PassageQueryService.Kind.TAG, "reviewed")).passages.single()
        assertEquals(body, tagged.pointer.element!!.let { it.bodyRange.substring(it.containingFile.text) })
        val document = FileDocumentManager.getInstance().getDocument(start.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            val offset = document.text.indexOf("Continue->Across files") + "Continue->".length
            document.replaceString(offset, offset + "Across files".length, "Missing")
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertTrue(model.children(model.at(start.passages.single { it.name == "Arrival" })!!,
            TweeHierarchyModel.View.OUTGOING).rows.any { it.label.contains("Unresolved") })
    }
}
