package twee.scope

import java.nio.file.Path

/** Shared ownership/exclusion policy. No filesystem traversal, PSI or indexes. */
object StoryPaths {
    private val ignored = setOf(".git", ".idea", ".gradle", ".kotlin", "node_modules", "build", "out", "dist", "storyformats", "tweego")
    fun normalize(value: String): String? {
        if (value.isBlank()) return null
        val slash = value.replace('\\', '/')
        if (slash.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(slash)) return null
        val parts = ArrayDeque<String>()
        for (part in slash.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeLast()
            else -> parts.addLast(part)
        }
        return parts.joinToString("/").ifEmpty { "." }
    }
    fun under(path: String, root: String) = root == "." || path == root || path.startsWith("$root/")
    fun contains(path: String, storyId: String, options: StorySettings.Options, localExclusions: List<String> = emptyList()): Boolean {
        val normalized = normalize(path) ?: return false
        if (normalized.split('/').any { it in ignored }) return false
        val selected = options.stories.singleOrNull { it.id == storyId } ?: return false
        val exclusions = selected.exclusions + options.stories.map { it.outputDirectory } + localExclusions
        if (exclusions.mapNotNull(::normalize).any { under(normalized, it) }) return false
        val owners = options.stories.flatMap { story -> story.roots.mapNotNull { root ->
            normalize(root)?.takeIf { under(normalized, it) }?.let { story.id to if (it == ".") 0 else it.length }
        } }
        val specificity = owners.maxOfOrNull { it.second } ?: return false
        return owners.filter { it.second == specificity }.map { it.first }.toSet() == setOf(storyId)
    }
    fun relative(base: String, path: String): String? {
        val root = Path.of(base).toAbsolutePath().normalize()
        val candidate = Path.of(path).toAbsolutePath().normalize()
        return if (candidate.startsWith(root)) root.relativize(candidate).toString().replace('\\', '/') else null
    }
}
