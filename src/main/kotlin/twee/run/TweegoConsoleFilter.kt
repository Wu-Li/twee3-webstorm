package twee.run

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import twee.scope.StoryScopeService
import java.nio.file.Path

/** Only Tweego's documented-by-source load diagnostics carry dependable file/line coordinates. */
internal class TweegoConsoleFilter(private val project: Project, private val storyId: String? = null) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val location = location(line) ?: return null
        if (project.isDisposed) return null
        val path = try { Path.of(location.path) } catch (_: IllegalArgumentException) { return null }
        if (!path.isAbsolute) return null // Builds pass absolute inputs; never guess a diagnostic's base directory.
        val file = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return null
        val scope = project.getService(StoryScopeService::class.java).snapshot(storyId) ?: return null
        if (!scope.contains(file)) return null
        val base = entireLength - line.length
        return Filter.Result(base + location.start, base + location.end, FileHyperlinkInfo(project, file, location.line - 1))
    }
    data class Location(val path: String, val line: Int, val start: Int, val end: Int)
    companion object {
        private val pattern = Regex("^\\s*(?:error|warning): load (.+): line ([0-9]+):")
        fun location(text: String): Location? {
            val match = pattern.find(text) ?: return null
            val number = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val file = match.groups[1]!!
            return Location(file.value, number, file.range.first, file.range.last + 1)
        }
    }
}
