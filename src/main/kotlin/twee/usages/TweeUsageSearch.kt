package twee.usages

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import twee.psi.TweePassage
import twee.relations.RelationFact
import twee.relations.RelationQueryService
import twee.resolve.PassageReference
import twee.resolve.PassageTargetPsi
import twee.scope.StoryScopeService

/** Exact, indexed search path shared by Find Usages and ReferencesSearch. */
object TweeUsageSearch {
    fun inScope(scope: SearchScope, element: PsiElement, range: TextRange): Boolean = when (scope) {
        is GlobalSearchScope -> element.containingFile.virtualFile?.let(scope::contains) == true
        is LocalSearchScope -> scope.scope.any {
            it.containingFile == element.containingFile && it.textRange.contains(range)
        }
        else -> false
    }
    fun process(target: PsiElement, scope: SearchScope, consumer: Processor<in PsiReference>): Boolean {
        ProgressManager.checkCanceled()
        val project = target.project
        val service = project.getService(RelationQueryService::class.java)
        val selected = project.getService(StoryScopeService::class.java).snapshot() ?: return true
        if (DumbService.isDumb(project) || PsiDocumentManager.getInstance(project).uncommittedDocuments.any {
                FileDocumentManager.getInstance().getFile(it)?.let(selected::contains) == true
            }) throw ProcessCanceledException()
        val result = when (target) {
            is TweePassage -> {
                val file = target.containingFile.virtualFile ?: return true
                if (!selected.contains(file)) return true
                service.incomingPassage(target.name)
            }
            is TweeLogicalSymbol -> service.occurrences(target.identity() ?: return true)
            else -> return true
        }
        // Do not turn a temporarily unavailable index/document snapshot into a false "no usages" result.
        if (result.state in setOf(RelationQueryService.State.INDEXING, RelationQueryService.State.DOCUMENTS_UNCOMMITTED)) throw ProcessCanceledException()
        if (result.state != RelationQueryService.State.READY) return true
        val seen = hashSetOf<Triple<String, Int, Int>>()
        for (occurrence in result.occurrences) {
            ProgressManager.checkCanceled()
            val fact = occurrence.fact
            if (fact.kind == RelationFact.Kind.MACRO_BINDING) continue // metadata duplicates the write at this physical site
            val leaf = occurrence.pointer.element ?: continue
            if (!inScope(scope, leaf, fact.range)) continue
            if (!seen.add(Triple(occurrence.fileUrl, fact.start, fact.end))) continue
            val reference: PsiReference = if (target is TweePassage) {
                var parent: PsiElement? = leaf
                while (parent != null && parent !is PassageTargetPsi) parent = parent.parent
                val psi = parent as? PassageTargetPsi ?: continue
                psi.references.filterIsInstance<PassageReference>().firstOrNull {
                    it.rangeInElement.shiftRight(psi.textRange.startOffset) == fact.range && it.isReferenceTo(target)
                } ?: continue
            } else {
                // Use a containing physical element if the name spans several lexical leaves.
                var anchor = leaf
                while (!anchor.textRange.contains(fact.range)) anchor = anchor.parent ?: break
                if (!anchor.textRange.contains(fact.range)) continue
                LogicalUsageReference(anchor, fact.range.shiftLeft(anchor.textRange.startOffset), target as TweeLogicalSymbol, fact)
            }
            if (!consumer.process(reference)) return false
        }
        return true
    }
}

class LogicalUsageReference(element: PsiElement, range: TextRange, private val target: TweeLogicalSymbol, val fact: RelationFact) :
    PsiReferenceBase<PsiElement>(element, range, true) {
    override fun resolve(): PsiElement? = target.takeIf { it.isValid }
    override fun getVariants(): Array<Any> = emptyArray()
    override fun isReferenceTo(element: PsiElement) = target.isEquivalentTo(element)
}

class TweeReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean =
        ReadAction.compute<Boolean, RuntimeException> {
            val target = TweeSymbols.target(parameters.elementToSearch) ?: return@compute true
            TweeUsageSearch.process(target, parameters.effectiveSearchScope, consumer)
        }
}
