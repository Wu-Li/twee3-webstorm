package twee.hierarchy

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import twee.testing.StoryProjectTestCase
import twee.psi.TweeFile
import twee.scope.StoryContextService
import twee.scope.StorySettings
import twee.hierarchy.TweeHierarchyModel.View

class TweeHierarchyTest : StoryProjectTestCase() {
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
    private fun model() = TweeHierarchyModel(project)
    fun testGroupedLinksAndIncomingOwners() {
        val first = myFixture.addFileToProject("one/a.tw", ":: A\n[[B]] [[label->B]] (display:'B')") as TweeFile
        val second = myFixture.addFileToProject("one/b.tw", ":: B") as TweeFile
        myFixture.addFileToProject("two/other.tw", ":: Other\n[[B]]")
        ready()
        val outgoing = model().children(model().at(first.passages.single())!!, View.OUTGOING)
        val links = outgoing.rows.single { it.label.startsWith("link") }
        assertEquals(2, links.sites.size)
        assertEquals("B", (links.target!!.element as twee.psi.TweePassage).name)
        val incoming = model().children(model().at(second.passages.single())!!, View.INCOMING)
        assertEquals(2, incoming.rows.size)
        assertTrue(incoming.rows.all { it.target!!.element!!.containingFile == first })
    }
    fun testCycleNodesStopRecursiveExpansion() {
        val file = myFixture.addFileToProject("one/cycle.tw", ":: A\n[[B]]\n:: B\n[[A]]") as TweeFile
        ready()
        val tree = TweeHierarchyTree(project, model().at(file.passages.first())!!, View.OUTGOING)
        val b = tree.getChildElements(tree.rootElement).filterIsInstance<TweeHierarchyDescriptor>().single { it.target != null }
        val a = tree.getChildElements(b).filterIsInstance<TweeHierarchyDescriptor>().single { it.target != null }
        assertTrue(a.terminal); assertTrue(a.label.contains("cycle"))
        assertTrue(tree.getChildElements(a).filterIsInstance<TweeHierarchyDescriptor>().all { it.target == null && it.terminal })
    }
    fun testDuplicateDestinationsAndDynamicTargets() {
        val source = myFixture.addFileToProject("one/source.tw", ":: Start\n[[Target]][[\$dynamic]]") as TweeFile
        myFixture.addFileToProject("one/targets.tw", ":: Target\n:: Target")
        ready()
        val offset = source.text.indexOf("Target")
        val root = model().at(source.findElementAt(offset)!!, offset)!!
        assertEquals(TweeHierarchyModel.Kind.CHOICE, root.kind)
        val choices = model().children(root, View.INCOMING).rows
        assertEquals(2, choices.size)
        assertEquals(2, choices.map { it.target!!.label() }.distinct().size)
        val outgoing = model().children(model().at(source.passages.single())!!, View.OUTGOING)
        assertTrue(outgoing.rows.any { it.target == null && it.label.startsWith("Dynamic") && it.sites.size == 1 })
    }
    fun testCustomBodiesCandidatesAndBuiltinTerminal() {
        val file = myFixture.addFileToProject("one/macros.tw", ":: A\n(set:\$m to (macro:[(print:1)[[B]]]))(set:\$m to (macro:[(print:2)]))(\$m:)\n:: B") as TweeFile
        ready()
        val offset = file.text.lastIndexOf("\$m")
        val root = model().at(file.findElementAt(offset)!!, offset)!!
        assertEquals(View.INCOMING, model().defaultView(root))
        val bodies = model().children(root, View.OUTGOING).rows
        assertEquals(2, bodies.size)
        assertTrue(bodies.all { it.label.contains("Candidate") })
        val bodyRows = model().children(bodies.first().target!!, View.OUTGOING).rows
        assertTrue(bodyRows.any { it.label.startsWith("link") })
        val builtin = bodyRows.first { it.label.startsWith("call") }.target!!
        assertTrue(model().children(builtin, View.OUTGOING).status!!.contains("Engine macro"))
    }
    fun testReadsWritesModeAndUnknownBinding() {
        val file = myFixture.addFileToProject("one/vars.tw", ":: A\n(set:\$x to 1)(print:\$x)(print:_unknown)")
        ready()
        val x = model().at(file.findElementAt(file.text.indexOf("\$x"))!!)!!
        assertEquals(View.READS, model().defaultView(x))
        assertEquals(1, model().children(x, View.READS).rows.single().sites.size)
        assertEquals(1, model().children(x, View.WRITES).rows.single().sites.size)
        val unknown = model().at(file.findElementAt(file.text.indexOf("_unknown"))!!)!!
        assertEquals(1, model().children(unknown, View.READS).rows.size)
        assertEmpty(model().children(unknown, View.WRITES).rows)
    }
    fun testFreshModelUsesUnsavedEditsAndScopeChanges() {
        val file = myFixture.addFileToProject("one/edit.tw", ":: A\n[[B]]\n:: B") as TweeFile
        ready()
        val root = model().at(file.passages.first())!!
        assertEquals(1, model().children(root, View.OUTGOING).rows.size)
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) { document.replaceString(document.text.indexOf("[[B]]"), document.text.indexOf("[[B]]") + 5, "[[Missing]]") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertTrue(model().children(root, View.OUTGOING).rows.single().label.contains("Unresolved"))
        val settings = project.getService(StorySettings::class.java)
        val state = settings.state; state.selectedStoryId = "b"; settings.loadState(state)
        assertTrue(model().children(root, View.OUTGOING).status!!.contains("outside"))
    }
}
