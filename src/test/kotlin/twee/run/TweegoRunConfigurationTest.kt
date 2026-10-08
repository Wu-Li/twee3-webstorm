package twee.run

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element
import twee.scope.StorySettings
import java.nio.file.Files

class TweegoRunConfigurationTest : BasePlatformTestCase() {
    private fun configuration(): TweegoRunConfiguration {
        val factory = TweegoConfigurationType().configurationFactories.single()
        return factory.createTemplateConfiguration(project) as TweegoRunConfiguration
    }
    fun testOptionsRoundTripAndCloneIsolation() {
        val original = configuration()
        original.buildOptions.apply {
            storyId = "story-b"; workingDirectory = "stories/雪 & space"
            outputPath = "site/index.html"; formatId = "harlowe-custom"
            environmentText = "TWEEGO_PATH=/my formats\nCUSTOM=a=b & c"
            browserId = "browser-id"; openBrowser = false
        }
        val xml = Element("configuration"); original.writeExternal(xml)
        val restored = configuration(); restored.readExternal(xml)
        assertEquals(original.buildOptions.request(), restored.buildOptions.request())
        assertEquals("browser-id", restored.buildOptions.browserId)
        assertFalse(restored.buildOptions.openBrowser)
        val clone = original.clone() as TweegoRunConfiguration
        clone.buildOptions.storyId = "another"; clone.buildOptions.openBrowser = true
        assertEquals("story-b", original.buildOptions.storyId); assertFalse(original.buildOptions.openBrowser)
    }
    fun testDefaultsAndEnvironmentValidation() {
        val config = configuration()
        assertTrue(config.buildOptions.openBrowser)
        assertEquals("dist/index.html", config.buildOptions.request().outputPath)
        assertNull(config.buildOptions.request().storyId)
        assertEquals(mapOf("KEY" to " a=b & 雪", "EMPTY" to ""), TweegoRunOptions.parseEnvironment("KEY= a=b & 雪\nEMPTY="))
        for (bad in listOf("missing equals", "=value", "KEY=a\nKEY=b", "BAD NAME=value", "KEY=\u0000")) {
            try { TweegoRunOptions.parseEnvironment(bad); fail("Accepted invalid environment") }
            catch (_: IllegalArgumentException) {}
        }
    }
    fun testUnknownStoryNeverFallsBackAndBadPathsRejected() {
        project.getService(StorySettings::class.java).loadState(StorySettings.Options(
            mutableListOf(StorySettings.Story("a", "A", mutableListOf("story"))), "a"))
        val config = configuration(); config.checkConfiguration()
        config.buildOptions.storyId = "deleted"
        try { config.checkConfiguration(); fail() } catch (_: RuntimeConfigurationError) {}
        config.buildOptions.storyId = "a"; config.buildOptions.workingDirectory = "../outside"
        try { config.checkConfiguration(); fail() } catch (_: RuntimeConfigurationError) {}
        config.buildOptions.workingDirectory = "story"; config.buildOptions.outputPath = "source.twee"
        try { config.checkConfiguration(); fail() } catch (_: RuntimeConfigurationError) {}
    }
    fun testLaunchRequiresSuccessfulPromotionAndRunIntent() {
        val html = Files.createTempFile("twee-launch", ".html")
        try {
            Files.writeString(html, "last good HTML")
            var calls = 0
            val open: (java.nio.file.Path) -> Unit = { assertEquals(html, it); calls++ }
            TweegoLaunchPolicy.open(TweegoBuildService.Result(error = "compile failed"), true, false, open)
            TweegoLaunchPolicy.open(TweegoBuildService.Result(output = html, canceled = true), true, false, open)
            TweegoLaunchPolicy.open(TweegoBuildService.Result(output = html), false, false, open)
            TweegoLaunchPolicy.open(TweegoBuildService.Result(output = html), true, true, open)
            assertEquals(0, calls)
            TweegoLaunchPolicy.open(TweegoBuildService.Result(output = html), true, false, open)
            assertEquals(1, calls)
            Files.delete(html)
            try { TweegoLaunchPolicy.open(TweegoBuildService.Result(output = html), true, false, open); fail() }
            catch (_: IllegalStateException) {}
            assertEquals(1, calls)
        } finally { Files.deleteIfExists(html) }
    }
    fun testOnlyRecognizedLoadDiagnosticsProvideLocations() {
        val text = "error: load C:\\stories\\雪 & space.tw: line 12: Malformed twee source; no name.\n"
        val location = TweegoConsoleFilter.location(text)!!
        assertEquals("C:\\stories\\雪 & space.tw", location.path)
        assertEquals(location.path, text.substring(location.start, location.end)); assertEquals(12, location.line)
        assertNull(TweegoConsoleFilter.location("error: line 2: missing file coordinate"))
        assertNull(TweegoConsoleFilter.location("error: load /story.tw: line 0: invalid"))
        assertNull(TweegoConsoleFilter.location("unrecognized compiler output"))
    }
}
