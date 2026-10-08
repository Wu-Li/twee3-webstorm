package twee.resolve

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import twee.index.PassageQueryService
import twee.scope.StoryContextService
import twee.scope.StoryScopeService

/** Only LINK/MACRO composites get references; strings, comments and embedded code do not. */
class PassageTargetPsi(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReferences(): Array<PsiReference> {
        if (!project.getService(StoryContextService::class.java).supportsHarlowe(containingFile.virtualFile)) return PsiReference.EMPTY_ARRAY
        val target = PassageTargets.extract(node) ?: return PsiReference.EMPTY_ARRAY
        if (target.name.isNullOrEmpty() || target.range.isEmpty) return PsiReference.EMPTY_ARRAY
        return arrayOf(PassageReference(this, target))
    }
    override fun getReference(): PsiReference? = references.singleOrNull()
}

class PassageReference(element: PassageTargetPsi, val target: PassageTargets.Target) :
    PsiPolyVariantReferenceBase<PassageTargetPsi>(element, target.range, true) {
    override fun getCanonicalText(): String = target.name.orEmpty()
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project
        val file = element.containingFile.virtualFile ?: return ResolveResult.EMPTY_ARRAY
        val scope = project.getService(StoryScopeService::class.java).snapshot() ?: return ResolveResult.EMPTY_ARRAY
        if (!scope.contains(file) || !project.getService(StoryContextService::class.java).supportsHarlowe(file)) return ResolveResult.EMPTY_ARRAY
        val name = target.name ?: return ResolveResult.EMPTY_ARRAY
        val result = project.getService(PassageQueryService::class.java).query(
            PassageQueryService.Request(PassageQueryService.Kind.NAME, name))
        return result.passages.mapNotNull {
            ProgressManager.checkCanceled()
            it.pointer.element?.takeIf { passage -> passage.isValid }?.let { passage -> PsiElementResolveResult(passage) }
        }.toTypedArray()
    }
    override fun getVariants(): Array<Any> = emptyArray()
}
