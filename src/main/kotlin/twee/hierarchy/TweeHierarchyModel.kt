package twee.hierarchy

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import twee.parser.HarloweTypes
import twee.psi.TweeFile
import twee.psi.TweePassage
import twee.relations.RelationFact.Kind as K
import twee.relations.RelationQueryService
import twee.resolve.PassageReference
import twee.resolve.PassageTargetPsi
import twee.scope.StoryScopeService
import twee.usages.TweeLogicalSymbol
import twee.usages.TweeSymbols

/** Static edges only. All tree targets and source sites retain physical smart pointers. */
class TweeHierarchyModel(private val project: Project) {
    enum class Kind { PASSAGE, BODY, SYMBOL, CHOICE }
    enum class View(val title: String) { INCOMING("Incoming calls / links"), OUTGOING("Outgoing calls / links"), READS("Reads"), WRITES("Writes") }
    data class Target(val kind: Kind, val pointer: SmartPsiElementPointer<PsiElement>) {
        val element get() = pointer.element
        fun key(): String {
            val element = element ?: return "deleted"
            if (kind == Kind.SYMBOL) return "symbol:${(TweeSymbols.target(element) as? TweeLogicalSymbol)?.identity()}"
            return "$kind:${element.containingFile.virtualFile?.url}:${element.textRange.startOffset}"
        }
        fun label(): String {
            val element = element ?: return "Deleted source"
            val file = element.containingFile.virtualFile?.presentableUrl.orEmpty()
            val name = when (kind) {
                Kind.PASSAGE -> (element as TweePassage).name
                Kind.SYMBOL -> (TweeSymbols.target(element) as? TweeLogicalSymbol)?.name ?: "Changed symbol"
                Kind.BODY -> "Custom macro body in " + (element.containingFile as? TweeFile)?.passages?.firstOrNull { it.textRange.contains(element.textRange) }?.name.orEmpty()
                Kind.CHOICE -> "Passage destination candidates"
            }
            val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile)
            val line = document?.getLineNumber(element.textRange.startOffset)?.plus(1)
            return "$name — $file:${line ?: element.textRange.startOffset}"
        }
    }
    data class Row(val label: String, val target: Target?, val sites: List<SmartPsiElementPointer<PsiElement>> = emptyList())
    data class Result(val rows: List<Row> = emptyList(), val status: String? = null)
    private val service get() = project.getService(RelationQueryService::class.java)
    private fun target(element: PsiElement, kind: Kind) = Target(kind, SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element))

    fun at(element: PsiElement, offset: Int? = null): Target? {
        val physical = (element as? TweeLogicalSymbol)?.anchor ?: element
        val file = physical.containingFile as? TweeFile ?: return null
        val scope = project.getService(StoryScopeService::class.java).snapshot() ?: return null
        if (file.virtualFile?.let(scope::contains) != true) return null
        if (physical is TweePassage) return target(physical, Kind.PASSAGE)
        if (physical.node?.elementType == HarloweTypes.HOOK && TweeSymbols.facts(file).any { it.owner == physical.textRange.startOffset && it.owner != it.passage })
            return target(physical, Kind.BODY)
        var parent: PsiElement? = physical
        while (parent != null && parent !is TweePassage) {
            val candidate = parent as? PassageTargetPsi
            if (candidate != null && candidate.references.filterIsInstance<PassageReference>().any {
                    offset == null && candidate === physical || offset != null && it.rangeInElement.shiftRight(candidate.textRange.startOffset).containsOffset(offset)
                }) return target(candidate, Kind.CHOICE)
            parent = parent.parent
        }
        return when (val symbol = TweeSymbols.target(physical)) {
            is TweePassage -> target(symbol, Kind.PASSAGE)
            is TweeLogicalSymbol -> symbol.anchor?.let { target(it, Kind.SYMBOL) }
            else -> null
        }
    }
    fun defaultView(target: Target): View {
        if (target.kind != Kind.SYMBOL) return View.INCOMING
        val element = target.element ?: return View.INCOMING
        val facts = TweeSymbols.factsAt(element)
        return if (facts.any { it.kind == K.CUSTOM_CALL || it.kind == K.NAMED_CALL }) View.INCOMING else View.READS
    }
    private fun owner(occurrence: RelationQueryService.Occurrence): Target? {
        val file = occurrence.pointer.element?.containingFile as? TweeFile ?: return null
        val fact = occurrence.fact
        if (fact.owner == fact.passage) return file.passages.firstOrNull { it.textRange.startOffset == fact.passage }?.let { target(it, Kind.PASSAGE) }
        return body(file, fact.owner)
    }
    private fun body(file: PsiFile, offset: Int): Target? {
        var element = file.findElementAt(offset)
        while (element != null && element.node?.elementType != HarloweTypes.HOOK) element = element.parent
        return element?.let { target(it, Kind.BODY) }
    }
    private fun ready(result: RelationQueryService.Result): String? = when (result.state) {
        RelationQueryService.State.READY -> null
        RelationQueryService.State.INDEXING -> "Indexing — refresh when ready"
        RelationQueryService.State.DOCUMENTS_UNCOMMITTED -> "Waiting for document changes"
        RelationQueryService.State.NO_STORY -> "Select a story in Twee settings"
        RelationQueryService.State.UNSUPPORTED -> "Waiting for a supported Harlowe story context"
    }
    fun children(root: Target, view: View): Result {
        ProgressManager.checkCanceled()
        val element = root.element ?: return Result(status = "Source was deleted")
        val scope = project.getService(StoryScopeService::class.java).snapshot() ?: return Result(status = "Select a story")
        if (element.containingFile.virtualFile?.let(scope::contains) != true) return Result(status = "Source is outside the selected story")
        if (root.kind == Kind.CHOICE) {
            val ref = (element as? PassageTargetPsi)?.references?.filterIsInstance<PassageReference>()?.firstOrNull()
            val choices = ref?.multiResolve(false).orEmpty().mapNotNull { it.element as? TweePassage }
            return Result(choices.map { Row("Candidate: ${it.name}", target(it, Kind.PASSAGE)) },
                if (choices.isEmpty()) "No static passage candidate; destination may be missing or indexing" else null)
        }
        val symbol = if (root.kind == Kind.SYMBOL) TweeSymbols.target(element) as? TweeLogicalSymbol else null
        val identity = symbol?.identity()
        if (root.kind == Kind.SYMBOL && identity == null) return Result(status = "Symbol changed; open hierarchy again")
        if (view in setOf(View.READS, View.WRITES) && identity?.key?.startsWith("v:") != true)
            return Result(status = "Reads and Writes apply to variable symbols")
        if (identity != null) {
            val result = when (view) {
                View.READS -> service.reads(identity)
                View.WRITES -> service.writes(identity)
                else -> service.occurrences(identity)
            }
            ready(result)?.let { return Result(status = it) }
            if (view == View.OUTGOING) {
                if (identity.key.startsWith("m:")) return Result(status = "Engine macro — no source body")
                val bodies = result.occurrences.filter { it.fact.kind == K.MACRO_BINDING }.mapNotNull { occurrence ->
                    occurrence.pointer.element?.containingFile?.let { body(it, occurrence.fact.body) }
                }.distinctBy { it.key() }
                return Result(bodies.map { Row("Candidate body", it) }, if (bodies.isEmpty()) "Dynamic macro value — no direct source binding" else null)
            }
            val selected = if (view == View.INCOMING) result.occurrences.filter { it.fact.kind in setOf(K.NAMED_CALL, K.CUSTOM_CALL) } else result.occurrences
            return grouped(selected.mapNotNull { owner(it)?.let { destination -> Edge(it.fact.kind.toString(), destination, it.pointer) } })
        }
        if (view == View.INCOMING) {
            if (root.kind == Kind.BODY) {
                val file = element.containingFile as TweeFile
                val bindings = TweeSymbols.facts(file).filter { it.kind == K.MACRO_BINDING && it.body == element.textRange.startOffset }
                val rows = bindings.mapNotNull { file.findElementAt(it.start)?.let { leaf -> at(leaf) } }.distinctBy { it.key() }
                return Result(rows.map { Row("Candidate binding (expand for callers)", it) }, if (rows.isEmpty()) "Anonymous or dynamic custom body" else null)
            }
            val result = service.incomingPassage((element as TweePassage).name)
            ready(result)?.let { return Result(status = it) }
            return grouped(result.occurrences.mapNotNull { owner(it)?.let { dest -> Edge(it.fact.kind.toString(), dest, it.pointer) } })
        }
        val result = service.outgoing(element.containingFile.virtualFile.url, element.textRange.startOffset)
        ready(result)?.let { return Result(status = it) }
        val edges = mutableListOf<Edge>()
        val dynamic = mutableListOf<Row>()
        for (occurrence in result.occurrences) {
            ProgressManager.checkCanceled()
            val fact = occurrence.fact
            val leaf = occurrence.pointer.element ?: continue
            when (fact.kind) {
                K.LINK, K.DISPLAY, K.TRANSITION -> {
                    if (fact.candidate) { dynamic.add(Row("Dynamic ${fact.kind.name.lowercase()} — runtime target", null, listOf(occurrence.pointer))); continue }
                    var parent: PsiElement? = leaf
                    while (parent != null && parent !is PassageTargetPsi) parent = parent.parent
                    val refs = (parent as? PassageTargetPsi)?.references?.filterIsInstance<PassageReference>().orEmpty()
                    val candidates = refs.flatMap { it.multiResolve(false).toList() }.mapNotNull { it.element as? TweePassage }
                    if (candidates.isEmpty()) dynamic.add(Row("Unresolved ${fact.kind.name.lowercase()}: ${fact.name}", null, listOf(occurrence.pointer)))
                    candidates.forEach { edges.add(Edge(fact.kind.toString(), target(it, Kind.PASSAGE), occurrence.pointer)) }
                }
                K.NAMED_CALL, K.CUSTOM_CALL -> at(leaf)?.let { edges.add(Edge(if (fact.kind == K.CUSTOM_CALL) "CALL candidate" else "CALL", it, occurrence.pointer)) }
                K.READ, K.WRITE, K.READ_WRITE -> if (root.kind == Kind.BODY) at(leaf)?.let { edges.add(Edge(fact.kind.toString(), it, occurrence.pointer)) }
                else -> Unit
            }
        }
        return Result(grouped(edges).rows + dynamic)
    }
    private data class Edge(val kind: String, val target: Target, val site: SmartPsiElementPointer<PsiElement>)
    private fun grouped(edges: List<Edge>): Result = Result(edges.groupBy { it.kind to it.target.key() }.values.map { group ->
        val sites = group.map { it.site }.distinct()
        Row("${group.first().kind.lowercase().replace('_', '/')} · ${sites.size} occurrence(s)", group.first().target, sites)
    }.sortedBy { it.label + it.target?.label() })
}
