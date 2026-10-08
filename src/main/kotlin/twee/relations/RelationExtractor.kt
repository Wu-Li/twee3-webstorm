package twee.relations

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import twee.parser.HarloweTypes as H
import twee.parser.TweeTypes
import twee.psi.TweeFile
import twee.relations.RelationFact.Kind as K
import twee.resolve.PassageTargets

/** Tolerant structural extraction; no runtime evaluation, validators, or cross-file lookups. */
object RelationExtractor {
    private class Scope(val id: String, val parent: Scope?, val isolated: Boolean = false) {
        val forced = mutableSetOf<String>()
        val declared = mutableSetOf<String>()
        fun binding(name: String): Scope? {
            if (name in forced) return this
            if (!isolated) parent?.binding(name)?.let { return it }
            return if (name in declared) this else null
        }
        fun root(): Scope = if (isolated || parent == null) this else parent.root()
    }
    private data class Raw(val fact: RelationFact, val scope: Scope)
    private class Collector {
        val raw = mutableListOf<Raw>()
        val access = java.util.IdentityHashMap<ASTNode, K>()
        val hookScopes = java.util.IdentityHashMap<ASTNode, Scope>()
        val hookOwners = java.util.IdentityHashMap<ASTNode, Int>()
        var passage = 0
        fun emit(node: ASTNode, kind: K, name: String, scope: Scope, owner: Int, body: Int = -1, candidate: Boolean = false,
                 start: Int = node.startOffset, end: Int = node.startOffset + node.textLength) {
            raw.add(Raw(RelationFact(kind, name, start, end, passage, owner, body = body, candidate = candidate), scope))
            if (name.startsWith('_') && kind in setOf(K.WRITE, K.READ_WRITE, K.MACRO_BINDING)) scope.declared.add(name)
        }
        fun walk(nodes: List<ASTNode>, scope: Scope, owner: Int) {
            for ((index, node) in nodes.withIndex()) {
                ProgressManager.checkCanceled()
                when (node.elementType) {
                    H.MACRO -> {
                        val name = macroName(node)
                        var loop: Scope? = null
                        if (name in setOf("for", "loop")) {
                            val hook = nodes.drop(index + 1).firstOrNull { it.text.isNotBlank() && it.elementType != H.COMMENT }
                            if (hook?.elementType == H.HOOK) {
                                loop = Scope("loop:${node.startOffset}", scope)
                                hookScopes[hook] = loop
                            }
                        }
                        macro(node, scope, owner, loop)
                    }
                    H.HOOK -> {
                        val child = hookScopes[node] ?: Scope("hook:${node.startOffset}", scope)
                        walk(children(node), child, hookOwners[node] ?: owner)
                    }
                    H.EXPRESSION -> expression(children(node).filter { it.elementType !in setOf(H.PAREN_OPEN, H.PAREN_CLOSE) }, scope, owner)
                    H.LINK -> target(node, scope, owner)
                    H.VARIABLE, H.TEMP_VARIABLE -> emit(node, access[node] ?: K.READ, node.text, scope, owner,
                        candidate = access[node] == K.READ_WRITE)
                    else -> if (node.firstChildNode != null) walk(children(node), scope, owner)
                }
            }
        }
        fun target(node: ASTNode, scope: Scope, owner: Int) {
            val target = PassageTargets.extract(node) ?: return
            val kind = when (target.kind) { PassageTargets.Kind.LINK -> K.LINK; PassageTargets.Kind.DISPLAY -> K.DISPLAY; PassageTargets.Kind.TRANSITION -> K.TRANSITION }
            emit(node, kind, target.name.orEmpty(), scope, owner, candidate = target.name == null,
                start = node.startOffset + target.range.startOffset, end = node.startOffset + target.range.endOffset)
        }
        fun macro(node: ASTNode, scope: Scope, owner: Int, loop: Scope?) {
            val content = significant(children(node)).drop(1)
            val head = content.firstOrNull() ?: return
            val name = macroName(node)
            if (variable(head)) {
                emit(head, K.CUSTOM_CALL, head.text, scope, owner, candidate = true)
                emit(head, K.READ, head.text, scope, owner)
            } else if (name.isNotEmpty()) {
                emit(node, K.NAMED_CALL, name, scope, owner, start = node.startOffset + 1, end = node.startOffset + header(node).length - 1)
            }
            target(node, scope, owner)
            val args = arguments(node)
            if (name == "macro") {
                val hook = args.lastOrNull()?.singleOrNull()?.takeIf { it.elementType == H.HOOK }
                if (hook != null) {
                    val local = Scope("macro:${hook.startOffset}", null, true)
                    hookScopes[hook] = local; hookOwners[hook] = hook.startOffset
                    for (arg in args.dropLast(1)) {
                        val parameter = arg.lastOrNull { it.elementType == H.TEMP_VARIABLE }
                        if (parameter != null) {
                            local.forced.add(parameter.text)
                            emit(parameter, K.BINDING, parameter.text, local, hook.startOffset)
                        }
                        walk(arg.filter { it !== parameter }, scope, owner)
                    }
                    walk(listOf(hook), scope, owner)
                    return
                }
            }
            for ((index, arg) in args.withIndex()) {
                if (name in setOf("set", "put", "move", "unpack")) {
                    val separator = arg.indexOfFirst { it.elementType == H.OPERATOR && it.text == (if (name == "set") "to" else "into") }
                    if (separator >= 0) {
                        val destination = if (name == "set") arg.take(separator) else arg.drop(separator + 1)
                        val value = if (name == "set") arg.drop(separator + 1) else arg.take(separator)
                        markDestination(destination)
                        if (name == "move" && value.any { it.elementType == H.PROPERTY }) baseVariable(value)?.let { access[it] = K.READ_WRITE }
                        val dest = destination.singleOrNull()?.takeIf(::variable)
                        val creation = value.singleOrNull()?.takeIf { it.elementType == H.MACRO && macroName(it) == "macro" }
                        val hook = creation?.let(::arguments)?.lastOrNull()?.singleOrNull()?.takeIf { it.elementType == H.HOOK }
                        if (dest != null && hook != null) emit(dest, K.MACRO_BINDING, dest.text, scope, owner, hook.startOffset, true)
                        walk(destination, scope, owner)
                        expression(value, scope, owner)
                        continue
                    }
                }
                expression(arg, scope, owner, if (index == 0) loop else null)
            }
        }
        fun markDestination(nodes: List<ASTNode>) {
            val vars = nodes.filter(::variable)
            if (vars.isNotEmpty()) {
                val base = baseVariable(nodes)!!
                access[base] = if (nodes.any { it.elementType == H.PROPERTY }) K.READ_WRITE else if (vars.size == 1) K.WRITE else K.READ_WRITE
            } else {
                // Destructuring and computed destinations are conservative candidates, not definite writes.
                fun mark(node: ASTNode) {
                    if (variable(node)) access[node] = K.READ_WRITE
                    else children(node).forEach(::mark)
                }
                nodes.forEach(::mark)
            }
        }
        fun expression(nodes: List<ASTNode>, parent: Scope, owner: Int, loop: Scope? = null) {
            val clauses = nodes.withIndex().filter { it.value.elementType == H.OPERATOR && it.value.text in setOf("each", "where", "via", "making", "when") }
            val scope = if (clauses.isNotEmpty()) loop ?: Scope("lambda:${nodes.first().startOffset}", parent) else parent
            val binders = mutableSetOf<ASTNode>()
            if (clauses.isNotEmpty()) {
                val firstClause = clauses.first()
                if (firstClause.value.text != "when") nodes.take(firstClause.index).lastOrNull { it.elementType == H.TEMP_VARIABLE }?.let(binders::add)
                for ((index, clause) in clauses) if (clause.text in setOf("each", "making"))
                    nodes.drop(index + 1).firstOrNull { it.elementType == H.TEMP_VARIABLE }?.let(binders::add)
                for (binder in binders) {
                    scope.forced.add(binder.text)
                    emit(binder, K.BINDING, binder.text, scope, owner)
                }
            }
            for ((index, node) in nodes.withIndex()) if (node.elementType == H.OPERATOR && node.text == "bind") {
                baseVariable(nodes.drop(index + 1))?.let { access[it] = K.READ_WRITE }
            }
            // The inherited lexer deliberately has no 2bind token: recognise its
            // contiguous raw prefix without changing highlighting parity.
            for ((index, node) in nodes.withIndex()) if (variable(node)) {
                val prefix = nodes.take(index).takeLast(5)
                if (prefix.joinToString("") { it.text } == "2bind" &&
                    prefix.zipWithNext().all { (a, b) -> a.startOffset + a.textLength == b.startOffset }) access[node] = K.READ_WRITE
            }
            walk(nodes.filter { it !in binders }, scope, owner)
        }
    }
    private fun children(node: ASTNode) = node.getChildren(null).toList()
    private fun significant(nodes: List<ASTNode>) = nodes.filter { it.text.isNotBlank() && it.elementType != H.COMMENT }
    private fun variable(node: ASTNode) = node.elementType == H.VARIABLE || node.elementType == H.TEMP_VARIABLE
    private fun baseVariable(nodes: List<ASTNode>): ASTNode? =
        if (nodes.any { it.elementType == H.PROPERTY && it.text == "of" }) nodes.lastOrNull(::variable) else nodes.firstOrNull(::variable)
    private val opener = Regex("^\\((?:[\u0024_]?[A-Za-z][-\\w]*|-[-\\w]*):")
    private fun header(node: ASTNode) = opener.find(node.text)?.value.orEmpty()
    private fun macroName(node: ASTNode): String {
        val name = header(node).drop(1).dropLast(1)
        return if (name.startsWith('$') || name.startsWith('_')) "" else RelationFact.normalizeMacro(name)
    }
    private fun arguments(node: ASTNode): List<List<ASTNode>> {
        val end = node.startOffset + header(node).length
        val body = significant(children(node)).filter { it.startOffset >= end }.toMutableList()
        if (body.lastOrNull()?.elementType == H.MACRO_CLOSE) body.removeAt(body.lastIndex)
        val args = mutableListOf<MutableList<ASTNode>>(mutableListOf())
        for (child in body) if (child.elementType == H.PUNCTUATION && child.text == ",") args.add(mutableListOf()) else args.last().add(child)
        return args
    }
    fun extract(file: TweeFile): List<RelationFact> {
        val collector = Collector()
        for (passage in file.passages) {
            ProgressManager.checkCanceled()
            collector.passage = passage.textRange.startOffset
            val body = passage.node.findChildByType(TweeTypes.BODY) ?: continue
            collector.walk(children(body), Scope("passage:${collector.passage}", null, true), collector.passage)
        }
        return collector.raw.map { (fact, scope) ->
            fact.copy(scope = if (fact.isVariable && fact.name.startsWith('_')) (scope.binding(fact.name) ?: scope.root()).id else "")
        }.sortedWith(compareBy({ it.start }, { it.kind.ordinal }))
    }
}
