package twee.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import twee.relations.RelationFact
import twee.relations.RelationQueryService

/** Candidate source bodies only; aliases, parameters and runtime values are not evaluated. */
class CustomMacroReference(element: PassageTargetPsi, range: TextRange, private val name: String) :
    PsiPolyVariantReferenceBase<PassageTargetPsi>(element, range, true) {
    override fun getCanonicalText() = name
    override fun getVariants(): Array<Any> = emptyArray()
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val file = element.containingFile.virtualFile ?: return ResolveResult.EMPTY_ARRAY
        val service = element.project.getService(RelationQueryService::class.java)
        val source = service.query("v:$name").occurrences.firstOrNull {
            it.fileUrl == file.url && it.fact.start == element.textRange.startOffset + rangeInElement.startOffset &&
                it.fact.kind == RelationFact.Kind.CUSTOM_CALL
        } ?: return ResolveResult.EMPTY_ARRAY
        return service.customBindings(source).candidates.mapNotNull { it.element?.let(::PsiElementResolveResult) }.toTypedArray()
    }
    companion object {
        private val call = Regex("^\\(([\u0024_][A-Za-z][A-Za-z0-9_]*):")
        fun create(element: PassageTargetPsi): CustomMacroReference? {
            val match = call.find(element.text) ?: return null
            val name = match.groupValues[1]
            return CustomMacroReference(element, TextRange(1, name.length + 1), name)
        }
    }
}
