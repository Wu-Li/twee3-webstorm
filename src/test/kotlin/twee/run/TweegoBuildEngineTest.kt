package twee.run

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException

class TweegoBuildEngineTest {
    private val root = Files.createTempDirectory("twee space ü & ")
    private val input = root.resolve("a & 雪.twee").also { Files.writeString(it, ":: Start\nHello") }
    private val script = root.resolve("script.js").also { Files.writeString(it, "window.demo = 1;") }
    private val css = root.resolve("style.css").also { Files.writeString(it, "body { color: red }") }
    private val output = root.resolve("dist/index.html")
    private val listing = TweegoBuildEngine.ProcessResult(1, stderr = """
        Available formats:
          ID                     Name (Version) [Details]
          harlowe old            Harlowe (3.1.0)
          harlowe-new            Harlowe (3.3.9)
          harlowe-future         Harlowe (4.0.0)
          sugarcube-2            SugarCube (2.37.0)
          proof                  Harlowe (3.9.0) [proofing]
    """.trimIndent())
    private fun plan(target: Path = output, formatId: String? = null) = TweegoBuildEngine.Plan(
        root.resolve("compiler & ü"), root, target, listOf(input, script, css), mapOf("TWEEGO_PATH" to "formats & ü"), formatId)
    private fun staged(command: TweegoBuildEngine.Command) = Path.of(command.arguments[command.arguments.indexOf("--output") + 1])
    private fun engine(compile: (TweegoBuildEngine.Command) -> TweegoBuildEngine.ProcessResult) = TweegoBuildEngine {
        if (it.arguments == listOf("--list-formats")) listing else compile(it)
    }
    private fun oldOutput() { Files.createDirectories(output.parent); Files.writeString(output, "last good") }
    private fun preserved() {
        assertEquals("last good", Files.readString(output))
        Files.list(output.parent).use { paths -> assertEquals(listOf(output), paths.toList()) }
    }
    private fun fails(block: () -> Unit) {
        try { block(); fail("Expected failure") } catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
    }
    @After fun cleanup() { Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }

    @Test fun argumentsAndSuccessfulPromotion() {
        oldOutput()
        val result = engine { command ->
            assertEquals(root, command.directory)
            assertEquals("formats & ü", command.environment["TWEEGO_PATH"])
            assertEquals(listOf("--format", "harlowe-new"), command.arguments.take(2))
            assertEquals(listOf(input, script, css).map { it.toString() }, command.arguments.drop(4))
            assertFalse(command.arguments.contains("--format-version"))
            assertEquals(0L, Files.size(staged(command)))
            Files.writeString(staged(command), "<!doctype html><html>new</html>")
            TweegoBuildEngine.ProcessResult(0)
        }.build(plan())
        assertEquals(output, result.output)
        assertEquals("harlowe-new", result.format.id)
        assertTrue(Files.readString(output).contains("new"))
        Files.list(output.parent).use { assertEquals(1L, it.count()) }
    }
    @Test fun nonzeroPreservesLastGoodAndCleansStaging() {
        oldOutput()
        fails { engine { Files.writeString(staged(it), "partial"); TweegoBuildEngine.ProcessResult(2) }.build(plan()) }
        preserved()
    }
    @Test fun missingOrEmptyOutputNeverPromotes() {
        oldOutput()
        fails { engine { TweegoBuildEngine.ProcessResult(0) }.build(plan()) }
        preserved()
        fails { engine { Files.delete(staged(it)); TweegoBuildEngine.ProcessResult(0) }.build(plan()) }
        preserved()
    }
    @Test fun cancellationAfterProcessSuccessNeverPromotes() {
        oldOutput(); var canceled = false
        val build = engine { Files.writeString(staged(it), "fresh"); canceled = true; TweegoBuildEngine.ProcessResult(0) }
        try {
            build.build(plan(), { if (canceled) throw CancellationException() })
            fail("Expected cancellation")
        } catch (_: CancellationException) {}
        preserved()
        // Reservation must have been released even on cancellation.
        engine { Files.writeString(staged(it), "retry"); TweegoBuildEngine.ProcessResult(0) }.build(plan())
        assertEquals("retry", Files.readString(output))
    }
    @Test fun cancellationBeforeDiscoveryStartsNoProcess() {
        var called = false
        try { engine { called = true; TweegoBuildEngine.ProcessResult(0) }.build(plan(), { throw CancellationException() }); fail() }
        catch (_: CancellationException) {}
        assertFalse(called); assertFalse(Files.exists(output))
    }
    @Test fun missingAndUnsupportedFormatsFailClosed() {
        oldOutput()
        for (id in listOf("missing", "sugarcube-2", "harlowe-future", "proof")) {
            fails { engine { fail("Must not compile"); TweegoBuildEngine.ProcessResult(0) }.build(plan(formatId = id)) }
        }
        fails { TweegoBuildEngine { TweegoBuildEngine.ProcessResult(1, stderr = "error: no formats") }.build(plan()) }
        preserved()
    }
    @Test fun explicitInstalledIdOverridesAutomaticHighest() {
        val result = engine {
            assertEquals("harlowe old", it.arguments[1])
            Files.writeString(staged(it), "html"); TweegoBuildEngine.ProcessResult(0)
        }.build(plan(formatId = "harlowe old"))
        assertEquals("3.1.0", result.format.version)
    }
    @Test fun concurrentSameTargetRejectedButDifferentTargetAllowed() {
        oldOutput()
        val nested = engine { Files.writeString(staged(it), "nested"); TweegoBuildEngine.ProcessResult(0) }
        engine {
            fails { nested.build(plan(root.resolve("dist/../dist/index.html"))) }
            nested.build(plan(root.resolve("dist/other.html")))
            Files.writeString(staged(it), "outer"); TweegoBuildEngine.ProcessResult(0)
        }.build(plan())
        assertEquals("outer", Files.readString(output))
        assertEquals("nested", Files.readString(root.resolve("dist/other.html")))
    }
    @Test fun processStartFailureCleansStaging() {
        oldOutput()
        fails { engine { error("cannot start executable") }.build(plan()) }
        preserved()
    }
    @Test fun missingExecutableAndEnvironmentPrecedence() {
        fails { TweegoBuildEngine.executable("missing compiler", emptyMap(), root) }
        fails { TweegoBuildEngine.executable("", mapOf("PATH" to root.toString()), root) }
        val environment = TweegoBuildEngine.environment(mapOf("TWEEGO_PATH" to "parent", "KEEP" to "yes"), listOf("local"), mapOf("TWEEGO_PATH" to "override"))
        assertEquals("override", environment["TWEEGO_PATH"]); assertEquals("yes", environment["KEEP"])
    }
    @Test fun resolvesConfiguredExecutableAndPathFallback() {
        val name = if (System.getProperty("os.name").startsWith("Windows", true)) "tweego.exe" else "tweego"
        val compiler = root.resolve(name)
        Files.writeString(compiler, "fake compiler")
        assertTrue(compiler.toFile().setExecutable(true))
        assertEquals(compiler.toRealPath(), TweegoBuildEngine.executable(compiler.toString(), emptyMap(), root))
        assertEquals(compiler.toRealPath(), TweegoBuildEngine.executable("", mapOf("PATH" to root.toString()), root))
    }
    @Test fun refusesNonHtmlDestinationAndUnsupportedInputs() {
        fails { engine { error("not reached") }.build(plan(input)) }
        val asset = root.resolve("picture.png").also { Files.writeString(it, "image") }
        fails { engine { error("not reached") }.build(plan().copy(inputs = listOf(input, asset))) }
        assertEquals(":: Start\nHello", Files.readString(input))
    }
}
