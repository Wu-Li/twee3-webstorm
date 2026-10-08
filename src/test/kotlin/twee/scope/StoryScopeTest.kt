package twee.scope

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import twee.parser.HarloweLexer

class StoryScopeTest : BasePlatformTestCase() {
    private fun story(id: String, vararg roots: String) = StorySettings.Story(id, id, roots.toMutableList())
    private fun options(vararg stories: StorySettings.Story) = StorySettings.Options(stories.toMutableList(), stories.first().id)
    fun testSpecificOwnershipExclusionsAndAmbiguity() {
        val a = story("a", "sources"); a.exclusions.add("sources/private")
        val b = story("b", "sources/other")
        val all = options(a, b)
        assertTrue(StoryPaths.contains("sources/start.twee", "a", all))
        assertFalse(StoryPaths.contains("sources/other/start.twee", "a", all))
        assertTrue(StoryPaths.contains("sources/other/start.twee", "b", all))
        assertFalse(StoryPaths.contains("sources/private/start.twee", "a", all))
        assertFalse(StoryPaths.contains("sources-other/start.twee", "a", all))
        b.roots.add("sources")
        assertFalse(StoryPaths.contains("sources/start.twee", "a", all))
        assertFalse(StoryPaths.contains("sources/start.twee", "b", all))
    }
    fun testExcludedGeneratedToolAndAssetInputs() {
        val all = options(story("a", ".")); all.stories.single().outputDirectory = "published"
        for (path in listOf("build/code.twee", "node_modules/code.twee", "storyformats/code.twee", ".git/code.tw", "published/code.tw", "compiler/code.tw"))
            assertFalse(path, StoryPaths.contains(path, "a", all, listOf("compiler")))
        assertNull(StoryPaths.normalize("../outside")); assertNull(StoryPaths.normalize("C:\\outside"))
        assertEquals("story/file.tw", StoryPaths.normalize("story/part/../file.tw"))
    }
    fun testPersistenceCopiesAndModificationTracking() {
        val settings = StorySettings(project); val all = options(story("a", "one"), story("b", "two"))
        val before = settings.modificationCount
        settings.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(all), StorySettings.Options::class.java))
        assertTrue(settings.modificationCount > before)
        assertEquals(all, settings.state)
        val copy = settings.state; copy.stories.first().roots.clear()
        assertEquals(listOf("one"), settings.state.stories.first().roots)
        val local = StoryLocalTools(); local.loadState(StoryLocalTools.Options("/tools/tweego", mutableListOf("/formats")))
        assertEquals("/tools/tweego", local.state.compilerPath)
        assertFalse(XmlSerializer.serialize(settings.state).toString().contains("/tools/tweego"))
    }
    fun testRunOverrideAndSnapshotInvalidation() {
        val settings = project.getService(StorySettings::class.java)
        settings.loadState(options(story("a", "one"), story("b", "two")))
        val service = project.getService(StoryScopeService::class.java)
        val one = myFixture.addFileToProject("one/start.twee", ":: Start").virtualFile
        val two = myFixture.addFileToProject("two/start.twee", ":: Start").virtualFile
        val selected = service.snapshot()!!
        assertTrue(selected.contains(one)); assertFalse(selected.contains(two))
        assertTrue(service.snapshot("b")!!.contains(two)); assertNull(service.snapshot("missing"))
        val changed = settings.state; changed.selectedStoryId = "b"; settings.loadState(changed)
        assertEquals("b", service.snapshot()!!.story.id)
        assertTrue(selected.contains(one)) // Existing query snapshots remain stable.
    }
    fun testEnumerationDeduplicatesRootsAndExcludesOtherStories() {
        val settings = project.getService(StorySettings::class.java)
        settings.loadState(options(story("a", "source", "source/part"), story("b", "source/other")))
        myFixture.addFileToProject("source/part/start.tw", ":: Start")
        myFixture.addFileToProject("source/site.css", "body{}")
        myFixture.addFileToProject("source/picture.png", "asset")
        myFixture.addFileToProject("source/other/start.tw", ":: Start")
        myFixture.addFileToProject("source/build/stale.tw", ":: Stale")
        val files = project.getService(StoryScopeService::class.java).snapshot()!!.files()
        assertEquals(listOf("site.css", "start.tw"), files.map { it.name }.sorted())
    }
    fun testBuildExcludesResolvedToolsAndIncludesSelectedScripts() {
        project.getService(StorySettings::class.java).loadState(options(story("a", "source")))
        myFixture.addFileToProject("source/start.tw", ":: Start")
        myFixture.addFileToProject("source/script.js", "window.demo = 1;")
        myFixture.addFileToProject("source/style.css", "body{}")
        val format = myFixture.addFileToProject("source/custom-formats/harlowe/format.js", "format source").virtualFile
        val scopes = project.getService(StoryScopeService::class.java)
        val plain = scopes.snapshot()!!
        val build = scopes.snapshot(additionalExcludedPaths = listOf(format.parent.parent.path))!!
        assertTrue(plain.contains(format)); assertFalse(build.contains(format))
        assertEquals(listOf("script.js", "start.tw", "style.css"), build.files().map { it.name }.sorted())
        assertTrue(plain.fingerprint != build.fingerprint)
    }
    fun testProjectStoryDataFromClosedAndUnsavedFiles() {
        val settings = project.getService(StorySettings::class.java)
        settings.loadState(options(story("a", "one"), story("b", "two")))
        val source = myFixture.addFileToProject("one/meta.tw", ":: StoryData\n{\"format\":\"Harlowe\",\"format-version\":\"3.3.9\"}")
        myFixture.addFileToProject("two/meta.tw", ":: StoryData\n{\"format\":\"SugarCube\",\"format-version\":\"2.37.0\"}")
        val scopes = project.getService(StoryScopeService::class.java)
        val context = project.getService(StoryContextService::class.java)
        assertEquals(StoryContextService.Format.HARLOWE_3, context.compute(scopes.snapshot()).format)
        assertEquals(StoryContextService.Format.UNSUPPORTED, context.compute(scopes.snapshot("b")).format)
        val document = FileDocumentManager.getInstance().getDocument(source.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(":: StoryData\n{bad}") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        assertEquals(StoryContextService.Format.INVALID, context.compute(scopes.snapshot()).format)
    }
    fun testMissingOverrideAndAmbiguousStoryData() {
        val settings = project.getService(StorySettings::class.java)
        val all = options(story("a", "story")); settings.loadState(all)
        myFixture.addFileToProject("story/start.tw", ":: Start")
        val scopes = project.getService(StoryScopeService::class.java)
        val context = project.getService(StoryContextService::class.java)
        assertEquals(StoryContextService.Format.MISSING, context.compute(scopes.snapshot()).format)
        all.stories.single().harlowe3WhenMissing = true; settings.loadState(all)
        assertEquals(StoryContextService.Format.HARLOWE_3, context.compute(scopes.snapshot()).format)
        myFixture.addFileToProject("story/meta.tw", ":: StoryData\n{}\n:: StoryData\n{}")
        assertEquals(StoryContextService.Format.AMBIGUOUS, context.compute(scopes.snapshot()).format)
    }
    fun testGenericHighlightingDoesNotRecognizeHarloweBody() {
        val lexer = HarloweLexer(false)
        lexer.start(":: Start\n(print: ${'$'}x) [[Next]]")
        val types = mutableListOf<String>()
        while (lexer.tokenType != null) { types.add(lexer.tokenType.toString()); lexer.advance() }
        assertTrue(types.contains("NAME")); assertFalse(types.contains("MACRO_NAME")); assertFalse(types.contains("LINK_OPEN"))
    }
}
