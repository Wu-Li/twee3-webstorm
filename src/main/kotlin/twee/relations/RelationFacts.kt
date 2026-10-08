package twee.relations

import com.intellij.openapi.util.TextRange

/** Persisted values are file facts only. Story identity is supplied by the querying service. */
data class RelationFact(
    val kind: Kind, val name: String, val start: Int, val end: Int,
    val passage: Int, val owner: Int, val scope: String = "", val body: Int = -1,
    val candidate: Boolean = false
) {
    enum class Kind { NAMED_CALL, CUSTOM_CALL, READ, WRITE, READ_WRITE, BINDING, MACRO_BINDING, LINK, DISPLAY, TRANSITION }
    val range get() = TextRange(start, end)
    val isVariable get() = kind in setOf(Kind.CUSTOM_CALL, Kind.READ, Kind.WRITE, Kind.READ_WRITE, Kind.BINDING, Kind.MACRO_BINDING)
    val reads get() = kind == Kind.READ || kind == Kind.READ_WRITE
    val writes get() = kind in setOf(Kind.WRITE, Kind.READ_WRITE, Kind.BINDING)
    val indexKey get() = when {
        isVariable -> "v:$name"
        kind == Kind.NAMED_CALL -> "m:$name"
        else -> "p:$name"
    }
    companion object {
        fun normalizeMacro(name: String) = name.lowercase().replace("-", "").replace("_", "")
    }
}
