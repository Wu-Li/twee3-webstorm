package twee.usages

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import com.intellij.usages.UsageTarget
import com.intellij.usages.PsiElementUsageTarget
import twee.psi.TweePassage
import twee.relations.RelationFact
import twee.resolve.PassageTargetPsi
import twee.resolve.PassageTargets

class TweeElementDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        val target = TweeSymbols.target(element) ?: return null
        val provider = TweeFindUsagesProvider()
        return if (location is UsageViewTypeLocation) provider.getType(target) else provider.getDescriptiveName(target)
    }
}

class TweeReadWriteAccessDetector : ReadWriteAccessDetector() {
    override fun isReadWriteAccessible(element: PsiElement): Boolean =
        (TweeSymbols.target(element) as? TweeLogicalSymbol)?.identity()?.key?.startsWith("v:") == true
    override fun isDeclarationWriteAccess(element: PsiElement) = TweeSymbols.factsAt(element).any { it.writes }
    override fun getReferenceAccess(referencedElement: PsiElement, reference: PsiReference): Access =
        if (reference is LogicalUsageReference) access(listOf(reference.fact)) else getExpressionAccess(reference.element)
    override fun getExpressionAccess(expression: PsiElement): Access = access(TweeSymbols.factsAt(expression))
    private fun access(facts: List<RelationFact>): Access {
        val read = facts.any { it.reads || it.kind == RelationFact.Kind.CUSTOM_CALL }
        val write = facts.any { it.writes }
        return when { read && write -> Access.ReadWrite; write -> Access.Write; else -> Access.Read }
    }
}

class TweeUsageTypeProvider : UsageTypeProviderEx {
    override fun getUsageType(element: PsiElement, targets: Array<out UsageTarget>): UsageType? {
        val symbols = targets.filterIsInstance<PsiElementUsageTarget>().mapNotNull { it.element }
        if (symbols.any { it is TweePassage }) return passageType(element)
        if (symbols.filterIsInstance<TweeLogicalSymbol>().any { it.identity()?.key?.startsWith("m:") == true }) return CALL
        return getUsageType(element)
    }
    private fun passageType(element: PsiElement): UsageType? {
        val target = (element as? PassageTargetPsi)?.let { PassageTargets.extract(it.node) }
        return when (target?.kind) {
            PassageTargets.Kind.LINK -> LINK
            PassageTargets.Kind.DISPLAY -> DISPLAY
            PassageTargets.Kind.TRANSITION -> TRANSITION
            else -> null
        }
    }
    override fun getUsageType(element: PsiElement): UsageType? {
        val facts = TweeSymbols.factsAt(element)
        if (facts.any { it.kind in setOf(RelationFact.Kind.NAMED_CALL, RelationFact.Kind.CUSTOM_CALL) }) return CALL
        if (facts.any { it.kind == RelationFact.Kind.READ_WRITE }) return READ_WRITE
        if (facts.any { it.writes }) return UsageType.WRITE
        if (facts.any { it.reads }) return UsageType.READ
        return passageType(element)
    }
    companion object {
        private val CALL = UsageType { "Macro call" }
        private val READ_WRITE = UsageType { "Read/write candidate" }
        private val LINK = UsageType { "Passage link" }
        private val DISPLAY = UsageType { "Passage display" }
        private val TRANSITION = UsageType { "Passage transition" }
    }
}
