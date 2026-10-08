package twee.resolve

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import twee.testing.StoryProjectTestCase
import twee.psi.TweePassage
import twee.scope.StorySettings
import twee.scope.StoryContextService
import com.intellij.testFramework.PlatformTestUtil

class PassageNavigationTest : StoryProjectTestCase() {
    override fun setUp() {
        super.setUp()
        project.getService(StorySettings::class.java).loadState(StorySettings.Options(mutableListOf(
            StorySettings.Story("a", "A", mutableListOf("one"), harlowe3WhenMissing = true),
            StorySettings.Story("b", "B", mutableListOf("two"), harlowe3WhenMissing = true)
        ), "a"))
    }
    private fun targets(file: PsiFile) = PsiTreeUtil.findChildrenOfType(file, PassageTargetPsi::class.java)
        .sortedBy { it.textOffset }
    private fun references(file: PsiFile): List<PassageReference> {
        ready()
        return targets(file).flatMap { it.references.toList() }.filterIsInstance<PassageReference>()
    }
    private fun ready() {
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(project)
        val context = project.getService(StoryContextService::class.java)
        context.requestRefresh()
        PlatformTestUtil.waitWithEventsDispatching("Harlowe context", {
            context.current.storyId == "a" && context.current.format == StoryContextService.Format.HARLOWE_3
        }, 10)
    }
    private fun configureStoryFile(name: String, text: String): PsiFile {
        val file = myFixture.addFileToProject("one/$name", text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return myFixture.file
    }

    fun testFourFormsPreciseRangesCaseAndEscaping() {
        val file = configureStoryFile("links.tw", ":: Start\r\n[[雪 !]][[label->雪 !]][[雪 !<-label]][[label|雪 !]][[Snow \\[雪\\]]]")
        val refs = references(file)
        assertEquals(listOf("雪 !", "雪 !", "雪 !", "雪 !", "Snow [雪]"), refs.map { it.canonicalText })
        assertEquals(listOf("雪 !", "雪 !", "雪 !", "雪 !", "Snow \\[雪\\]"), refs.map { it.rangeInElement.substring(it.element.text) })
        assertTrue(refs.all { it.isSoft })
        assertEquals("Target", PassageTargets.bracket("[[ label-> Target ]]")!!.name)
        assertEquals("C", PassageTargets.bracket("[[A->B->C]]")!!.name)
        assertEquals("A", PassageTargets.bracket("[[A<-B<-C]]")!!.name)
        assertEquals("A|B", PassageTargets.bracket("[[A\\|B]]")!!.name)
    }
    fun testDocumentedMacroArgumentsAndAliases() {
        val file = configureStoryFile("macros.tw", """
            :: Start
            (display: "A") (GO_TO: 'B') (redirect: "C")
            (link-goto: "label", "D") (link-goto: "E")
            (link-reveal-goto: "label", "F", (text-colour: red))
            (link-reveal-goto: "G") (print: "Not a passage")
            (link-goto: (str: 1, 2), "H")
        """.trimIndent())
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), references(file).map { it.canonicalText })
    }
    fun testComputedTargetsRemainDynamic() {
        val file = configureStoryFile("dynamic.tw", """
            :: Start
            [[label->${'$'}destination]] [[_destination]] [[(either: "A", "B")]]
            (go-to: "A" + "B") (display: ${'$'}name)
            (link-goto: "label", (either: "A", "B")) (go-to: "unfinished
        """.trimIndent())
        assertEmpty(references(file))
        assertEquals(6, targets(file).mapNotNull { PassageTargets.extract(it.node) }.count { it.name == null })
    }
    fun testExcludedContextsAndNoNewDiagnostics() {
        val file = configureStoryFile("contexts.tw", """
            :: Start
            <!-- [[Hidden]] (go-to: "Hidden") -->
            (print: "[[Hidden]] (go-to: 'Hidden')")
            <span title="[[Hidden]] (go-to: 'Hidden')">text</span>
            <script>const link = "[[Hidden]]";</script><style>p { content: "[[Hidden]]" }</style>
            [[Missing]]
            :: Code [script]
            [[Hidden]] (go-to: "Hidden")
            :: Style [stylesheet]
            [[Hidden]]
        """.trimIndent())
        assertEquals(listOf("Missing"), references(file).map { it.canonicalText })
        assertEmpty(myFixture.doHighlighting().filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR })
    }
    fun testDuplicatesPhysicalHeaderAndStoryIsolation() {
        val source = myFixture.addFileToProject("one/source.tw", ":: Start\n[[Target]][[target]][[Missing]]")
        val target = myFixture.addFileToProject("one/targets.twee", ":: Target\nfirst\n:: Target\nsecond")
        val foreign = myFixture.addFileToProject("two/source.tw", ":: Target\n[[Target]]")
        ready()
        val refs = references(source)
        val matches = refs[0].multiResolve(false).map { it.element as TweePassage }
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.containingFile == target && it.textOffset == it.nameIdentifier!!.textOffset })
        assertNull(refs[0].resolve())
        assertEmpty(refs[1].multiResolve(false)); assertEmpty(refs[2].multiResolve(false))
        assertEmpty(references(foreign)) // A different story must not expose active-story references.
        assertSame(matches[0], target.findElementAt(matches[0].textOffset)!!.parent.parent)
    }
    fun testCommittedUnsavedRenameMoveAndDelete() {
        val source = myFixture.addFileToProject("one/source.tw", ":: Start\n[[Target]]")
        val target = myFixture.addFileToProject("one/target.twee", ":: Target")
        val destination = myFixture.addFileToProject("two/anchor.tw", ":: Anchor").virtualFile.parent
        ready(); assertNotNull(references(source).single().resolve())
        val document = FileDocumentManager.getInstance().getDocument(target.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(":: Renamed") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertNull(references(source).single().resolve())
        val sourceDocument = FileDocumentManager.getInstance().getDocument(source.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) { sourceDocument.setText(":: Start\n[[Renamed]]") }
        PsiDocumentManager.getInstance(project).commitDocument(sourceDocument)
        assertNotNull(references(source).single().resolve())
        WriteCommandAction.runWriteCommandAction(project) { target.virtualFile.rename(this, "new-name.tw") }
        ready(); assertNotNull(references(source).single().resolve())
        WriteCommandAction.runWriteCommandAction(project) { target.virtualFile.move(this, destination) }
        ready(); assertNull(references(source).single().resolve())
        WriteCommandAction.runWriteCommandAction(project) { target.virtualFile.delete(this) }
        ready(); assertNull(references(source).single().resolve())
    }
}
