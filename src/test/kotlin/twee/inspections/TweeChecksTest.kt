package twee.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import twee.parser.TweeJson
import twee.settings.TweeCheckSettings

class TweeChecksTest : BasePlatformTestCase() {
    private val options = TweeCheckSettings.Options()
    private fun check(text: String, switches: TweeCheckSettings.Options = options) = StoryDataChecks.evaluate(text, switches)
    private fun highlights(text: String) = myFixture.run {
        configureByText("checks.twee", text)
        doHighlighting().filter { it.severity.myVal >= HighlightSeverity.WARNING.myVal }
    }
    fun testWhitespaceWarningAndSwitch() {
        val settings = project.getService(TweeCheckSettings::class.java)
        val source = "::NoSpace\ntext\n::\tTabbed\ntext\n:: Bad[Tag[nested]]\n"
        val warning = highlights(source).single()
        assertEquals(HighlightSeverity.WARNING, warning.severity)
        assertEquals(0, warning.startOffset); assertEquals(3, warning.endOffset)
        assertTrue(warning.description.contains("No space"))
        settings.loadState(options.copy(spaceAfterStartToken = false))
        assertEmpty(highlights(source))
    }
    fun testJsonSyntaxOnlyEditorErrors() {
        for (value in listOf("{}", "[]", "null", "false", "42", "\"text\"")) {
            assertEmpty(highlights(":: StoryData\n$value"))
        }
        for (value in listOf("", "{bad}", "{\"x\":1,}", "// comment\n{}", "\uFEFF{}", "{} {}")) {
            val error = highlights(":: StoryData\n$value").single()
            assertEquals(HighlightSeverity.ERROR, error.severity)
            assertEquals("Malformed StoryData JSON!", error.description)
        }
    }
    fun testUuidAcceptanceMatchesUuid10() {
        val accepted = listOf("00000000-0000-0000-0000-000000000000", "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF") +
            (1..8).map { "12345678-1234-${it}234-8234-123456789abc" }
        accepted.forEach { assertTrue(it, StoryDataChecks.validIfid(it)) }
        for (value in listOf("12345678-1234-9234-8234-123456789abc", "12345678-1234-1234-7234-123456789abc", "not-a-uuid"))
            assertFalse(value, StoryDataChecks.validIfid(value))
    }
    fun testFieldChecksDefaultsAndDisabledSwitches() {
        assertEquals(listOf("Malformed StoryData: IFID not found!", "Malformed StoryData: Story Format name not found!", "Malformed StoryData: Story Format version not found!"), check("{}").messages)
        assertEmpty(check("{}", options.copy(ifid = false, format = false, formatVersion = false)).messages)
        assertEquals(listOf("Malformed StoryData: Invalid IFID!"), check("{\"ifid\":true,\"format\":[],\"format-version\":{}}").messages)
        assertEmpty(check("{\"ifid\":\"00000000-0000-0000-0000-000000000000\",\"format\":true,\"format-version\":1}").messages)
        assertFalse(check("null").malformedJson)
        assertTrue(check("null").messages.single().startsWith("Malformed StoryData JSON:"))
    }
    fun testNoHarloweValidationAndMalformedHeaderFallback() {
        assertEmpty(highlights(":: Start\n(unknown: nonsense, ${'$'}missing) [[Absent]] (set: 1 to false)\n:: Bad {nope}\n:: Bad[nested[tag]]\n:: End"))
    }
    fun testSettingsRoundTripAndDefaults() {
        assertEquals(TweeCheckSettings.Options(true, true, true, true), TweeCheckSettings().state)
        val selected = TweeCheckSettings.Options(false, true, false, true)
        val xml = XmlSerializer.serialize(selected)
        val restored = XmlSerializer.deserialize(xml, TweeCheckSettings.Options::class.java)
        assertEquals(selected, restored)
        val settings = TweeCheckSettings(); settings.loadState(restored)
        restored.ifid = false
        assertTrue(settings.state.ifid)
    }
    fun testNotificationDedupRefreshAndRepair() {
        val file = myFixture.configureByText("notification.twee", ":: StoryData\n{}")
        val service = project.getService(StoryDataNotifications::class.java)
        service.update(file.virtualFile, check("{}").messages, 1)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val original = service.activeNotifications(file.virtualFile)
        assertEquals(3, original.size)
        service.update(file.virtualFile, check("{}").messages, 2)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEquals(original, service.activeNotifications(file.virtualFile))
        val settings = project.getService(TweeCheckSettings::class.java)
        settings.loadState(options.copy(ifid = false, format = false, formatVersion = false))
        service.refreshSettings()
        service.update(file.virtualFile, check("{}", settings.state).messages, 3)
        myFixture.doHighlighting(); PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEmpty(service.activeMessages(file.virtualFile))
        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(":: Start\nNo StoryData") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        myFixture.doHighlighting(); PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEmpty(service.activeMessages(file.virtualFile))
    }
    fun testOutdatedNotificationCannotReplaceNewerRepair() {
        val file = myFixture.configureByText("order.twee", ":: Start")
        val service = project.getService(StoryDataNotifications::class.java)
        service.update(file.virtualFile, emptyList(), 100)
        service.update(file.virtualFile, listOf("stale"), 99)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEmpty(service.activeMessages(file.virtualFile))
    }
    fun testDeepJsonIsValidWithoutRecursionLimit() {
        assertFalse(TweeJson.valid("\"\\u１２３４\""))
        assertTrue(TweeJson.valid("[".repeat(2000) + "0" + "]".repeat(2000)))
        assertFalse(TweeJson.valid("[".repeat(2000) + "0" + "]".repeat(1999)))
    }
}
