package twee.scope

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.psi.search.GlobalSearchScope

@Service(Service.Level.PROJECT)
class StoryScopeService(private val project: Project) {
    /** Null override follows selection; an unknown nonnull override fails closed, never falls back. */
    fun snapshot(overrideStoryId: String? = null, additionalExcludedPaths: List<String> = emptyList()): StoryScope? {
        val settings = project.getService(StorySettings::class.java).state
        val id = overrideStoryId ?: settings.selectedStoryId
        val story = settings.stories.singleOrNull { it.id == id } ?: return null
        val base = project.basePath ?: return null
        val local = project.getService(StoryLocalTools::class.java).state
        val toolPaths = local.formatDirectories + listOfNotNull(local.compilerPath.takeIf { it.isNotBlank() }?.let { java.nio.file.Path.of(it).parent?.toString() })
        return StoryScope(project, base, story.snapshot(), settings, toolPaths + additionalExcludedPaths)
    }
}

class StoryScope internal constructor(project: Project, private val base: String, val story: StorySettings.Story,
                                     private val options: StorySettings.Options, private val toolPaths: List<String>) : GlobalSearchScope(project) {
    internal val fingerprint = 31 * options.hashCode() + toolPaths.hashCode()
    override fun getDisplayName() = "Twee: ${story.name}"
    override fun isSearchInModuleContent(aModule: Module) = true
    override fun isSearchInLibraries() = false
    private fun eligible(file: VirtualFile): Boolean {
        if (!file.isValid || file.`is`(VFileProperty.SYMLINK)) return false
        val canonical = file.canonicalPath ?: return false
        val relative = StoryPaths.relative(base, canonical) ?: return false
        if (toolPaths.any { path ->
            val root = java.nio.file.Path.of(path).toAbsolutePath().normalize()
            java.nio.file.Path.of(canonical).normalize().startsWith(root)
        }) return false
        return StoryPaths.contains(relative, story.id, options)
    }
    override fun contains(file: VirtualFile) = !file.isDirectory && file.extension?.lowercase() in setOf("tw", "twee", "js", "css") && eligible(file)
    fun files(): List<VirtualFile> {
        val result = linkedSetOf<VirtualFile>()
        val visited = hashSetOf<String>()
        fun visit(file: VirtualFile) {
            ProgressManager.checkCanceled()
            if (!visited.add(file.url) || !eligible(file)) return
            if (file.isDirectory) file.children.forEach(::visit) else if (contains(file)) result.add(file)
        }
        for (root in story.roots.mapNotNull(StoryPaths::normalize)) {
            LocalFileSystem.getInstance().findFileByPath(java.nio.file.Path.of(base).resolve(root).normalize().toString())?.let(::visit)
        }
        return result.sortedBy { it.url }
    }
}
