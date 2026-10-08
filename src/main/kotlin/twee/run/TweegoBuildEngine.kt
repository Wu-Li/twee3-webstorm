package twee.run

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.ConcurrentHashMap

/** Process/filesystem policy, separate from IDE scheduling and document management. */
internal class TweegoBuildEngine(private val runner: Runner) {
    data class Command(val executable: Path, val arguments: List<String>, val directory: Path,
                       val environment: Map<String, String>)
    data class ProcessResult(val exitCode: Int, val stdout: String = "", val stderr: String = "")
    fun interface Runner { fun run(command: Command): ProcessResult }
    data class Format(val id: String, val version: String)
    data class Plan(val executable: Path, val directory: Path, val output: Path, val inputs: List<Path>,
                    val environment: Map<String, String>, val formatId: String? = null)
    data class Success(val output: Path, val format: Format)

    fun build(plan: Plan, checkCanceled: () -> Unit = {}, effectiveFormat: (Format) -> Unit = {}): Success {
        checkCanceled()
        require(Files.isDirectory(plan.directory)) { "Story working directory does not exist." }
        require(plan.inputs.isNotEmpty() && plan.inputs.any { it.extension() in setOf("tw", "twee") }) { "No eligible Twee inputs in this story." }
        require(plan.inputs.all { Files.isRegularFile(it, NOFOLLOW_LINKS) && it.extension() in setOf("tw", "twee", "js", "css") }) { "A selected input is missing or unsupported." }
        require(plan.output.extension() in setOf("html", "htm")) { "Output must be an HTML file." }
        Files.createDirectories(plan.output.toAbsolutePath().parent)
        // Resolve parent aliases before reserving: two configurations cannot replace the same file.
        val output = plan.output.toAbsolutePath().parent.toRealPath().resolve(plan.output.fileName)
        require(!Files.isSymbolicLink(output) && !Files.isDirectory(output)) { "Output cannot be a symlink or directory." }
        require(plan.inputs.none { it.toRealPath() == output || (Files.exists(output) && Files.isSameFile(it, output)) }) { "Output conflicts with an input." }
        check(active.add(output)) { "Another build already targets $output." }
        var staging: Path? = null
        try {
            checkCanceled()
            val listing = runner.run(Command(plan.executable, listOf("--list-formats"), plan.directory, plan.environment))
            checkCanceled()
            val formats = parseFormats(listing)
            val format = if (!plan.formatId.isNullOrBlank()) formats.singleOrNull { it.id == plan.formatId }
                else formats.maxWithOrNull(compareBy<Format>({ versionPart(it.version, 1) }, { versionPart(it.version, 2) }, { it.id }))
            requireNotNull(format) { "Installed Harlowe 3 format not found. Configure Tweego and format directories in Twee settings." }
            effectiveFormat(format)
            staging = Files.createTempFile(output.parent, ".twee-build-", ".html")
            val args = listOf("--format", format.id, "--output", staging.toString()) + plan.inputs.map { it.toAbsolutePath().toString() }
            checkCanceled()
            val result = runner.run(Command(plan.executable, args, plan.directory, plan.environment))
            checkCanceled()
            check(result.exitCode == 0) { "Tweego exited with code ${result.exitCode}; previous HTML preserved." }
            check(Files.isRegularFile(staging, NOFOLLOW_LINKS) && Files.size(staging) > 0) { "Tweego produced no fresh HTML; previous HTML preserved." }
            // Fail closed on filesystems without atomic replacement; do not risk the last good output.
            Files.move(staging, output, ATOMIC_MOVE, REPLACE_EXISTING)
            return Success(output, format)
        } finally {
            try { staging?.let { Files.deleteIfExists(it) } } finally { active.remove(output) }
        }
    }

    companion object {
        private val active = ConcurrentHashMap.newKeySet<Path>()
        private fun Path.extension() = fileName.toString().substringAfterLast('.', "").lowercase()
        private fun versionPart(version: String, part: Int) = version.split('.').getOrNull(part)?.toIntOrNull() ?: 0
        /** Tweego 2.x deliberately lists on stderr and exits 1 (usage.go). */
        fun parseFormats(result: ProcessResult): List<Format> {
            if (result.exitCode !in 0..1) return emptyList()
            val text = result.stdout + "\n" + result.stderr
            if (!text.lineSequence().any { it.trim() == "Available formats:" }) return emptyList()
            val row = Regex("^\\s*(.+?)\\s{3,}Harlowe\\s+\\((3\\.\\d+\\.\\d+)\\)\\s*$", RegexOption.IGNORE_CASE)
            return text.lineSequence().mapNotNull { row.matchEntire(it)?.let { match -> Format(match.groupValues[1], match.groupValues[2]) } }.distinct().toList()
        }
        fun environment(parent: Map<String, String>, directories: List<String>, overrides: Map<String, String>): Map<String, String> {
            val result = parent.toMutableMap()
            if (directories.isNotEmpty()) result["TWEEGO_PATH"] = (listOfNotNull(parent["TWEEGO_PATH"]?.takeIf { it.isNotBlank() }) + directories).joinToString(File.pathSeparator)
            // Explicit overrides take precedence, including TWEEGO_PATH.
            result.putAll(overrides)
            return result.toMap()
        }
        fun executable(configured: String, environment: Map<String, String>, directory: Path): Path {
            val windows = System.getProperty("os.name").startsWith("Windows", true)
            val names = if (windows) listOf("tweego.exe", "tweego.com") else listOf("tweego")
            val candidates = if (configured.isNotBlank()) listOf(directory.resolve(configured)) else {
                val path = environment.entries.firstOrNull { it.key.equals("PATH", windows) }?.value.orEmpty()
                path.split(File.pathSeparator).filter { it.isNotBlank() }.flatMap { entry -> names.map { directory.resolve(entry).resolve(it) } }
            }
            return candidates.firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
                ?.toRealPath() ?: error("Tweego executable not found. Set its local path in Twee settings or add it to PATH.")
        }
    }
}
