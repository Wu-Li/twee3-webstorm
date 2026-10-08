package twee.usages

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.tree.TokenSet
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import twee.parser.HarloweLexer
import twee.parser.HarloweTypes
import twee.parser.TweeTypes
import twee.psi.TweePassage

class TweeFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner() = DefaultWordsScanner(HarloweLexer(),
        TokenSet.create(TweeTypes.NAME, HarloweTypes.MACRO_NAME, HarloweTypes.VARIABLE, HarloweTypes.TEMP_VARIABLE),
        TokenSet.create(HarloweTypes.COMMENT), TokenSet.create(HarloweTypes.STRING, HarloweTypes.LINK_TEXT))
    override fun canFindUsagesFor(psiElement: PsiElement) = TweeSymbols.target(psiElement) != null
    override fun getHelpId(psiElement: PsiElement): String? = null
    override fun getType(element: PsiElement): String = when (val target = TweeSymbols.target(element)) {
        is TweePassage -> "passage"
        is TweeLogicalSymbol -> if (target.name.startsWith('$') || target.name.startsWith('_')) "variable" else "macro"
        else -> "Twee symbol"
    }
    override fun getDescriptiveName(element: PsiElement): String = when (val target = TweeSymbols.target(element)) {
        is TweePassage -> target.name
        is TweeLogicalSymbol -> target.name
        else -> element.text.orEmpty()
    }
    override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)
}

class TweeFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement) = TweeSymbols.target(element) != null
    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? =
        TweeSymbols.target(element)?.let(::TweeFindUsagesHandler)
}

class TweeFindUsagesHandler(target: PsiElement) : FindUsagesHandler(target) {
    override fun isSearchForTextOccurrencesAvailable(psiElement: PsiElement, isSingleFile: Boolean) = false
    override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions): Boolean =
        ReadAction.compute<Boolean, RuntimeException> {
            if (!options.isUsages) return@compute true
            TweeUsageSearch.process(element, options.searchScope, Processor { reference ->
                processor.process(UsageInfo(reference))
            })
        }
    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference> =
        ReadAction.compute<Collection<PsiReference>, RuntimeException> {
            val references = mutableListOf<PsiReference>()
            TweeUsageSearch.process(target, searchScope, Processor { references.add(it); true })
            references
        }
}
