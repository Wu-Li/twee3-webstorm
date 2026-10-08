package twee.passages

import twee.index.PassageQueryService

object PassageGroups {
    /** null is the untagged bucket; a literal tag named Untagged remains a separate group. */
    fun group(passages: List<PassageQueryService.Match>): Map<String?, List<PassageQueryService.Match>> {
        val groups = linkedMapOf<String?, MutableList<PassageQueryService.Match>>()
        for (passage in passages) {
            val tags: List<String?> = if (passage.tags.isEmpty()) listOf(null) else passage.tags.distinct()
            for (tag in tags) groups.getOrPut(tag) { mutableListOf() }.add(passage)
        }
        return groups.entries.sortedWith(compareBy({ it.key != null }, { it.key.orEmpty() })).associate { (tag, rows) ->
            tag to rows.sortedWith(compareBy({ it.name }, { it.pointer.virtualFile?.url.orEmpty() }))
        }
    }
    data class Selection(val all: Set<String>, val some: Set<String>)
    fun selection(tags: List<List<String>>): Selection {
        if (tags.isEmpty()) return Selection(emptySet(), emptySet())
        val all = tags.map { it.toSet() }.reduce { a, b -> a intersect b }
        return Selection(all.toSortedSet(), (tags.flatten().toSet() - all).toSortedSet())
    }
}
