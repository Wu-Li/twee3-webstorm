package twee.usages

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import twee.psi.TweeFile
import twee.psi.TweePassage
import twee.relations.RelationExtractor
import twee.relations.RelationFact
import twee.relations.RelationQueryService
import twee.scope.StoryContextService
import twee.scope.StoryScopeService
import com.intellij.util.IncorrectOperationException

/** An occurrence-backed symbol, never an invented first-write declaration. */
class TweeLogicalSymbol internal constructor(anchor: PsiElement, private val original: RelationQueryService.Symbol) : FakePsiElement(), PsiNamedElement {
    private val ownerProject = anchor.project
    override fun getProject() = ownerProject
    override fun getManager() = PsiManager.getInstance(ownerProject)
    override fun getLanguage() = twee.language.TweeLanguage
    private val pointer = SmartPointerManager.getInstance(anchor.project).createSmartPsiElementPointer(anchor)
    val anchor get() = pointer.element
    fun identity(): RelationQueryService.Symbol? {
        val live = anchor ?: return null
        val fact = TweeSymbols.factsAt(live).firstOrNull { it.indexKey == original.key } ?: return null
        val file = live.containingFile.virtualFile ?: return null
        return original.copy(file = if (fact.scope.isEmpty()) "" else file.url, scope = fact.scope)
    }
    override fun getParent(): PsiElement? = anchor?.parent
    override fun getContainingFile(): PsiFile? = anchor?.containingFile
    override fun getName() = original.key.substring(2)
    override fun setName(name: String): PsiElement = throw IncorrectOperationException("Logical symbol rename is not implemented")
    override fun getNavigationElement(): PsiElement = anchor ?: this
    override fun getTextOffset() = anchor?.textOffset ?: 0
    override fun getTextRange() = anchor?.textRange
    override fun getText() = anchor?.text.orEmpty()
    override fun getTextLength() = text.length
    override fun isValid() = anchor?.isValid == true && identity() != null
    override fun getUseScope(): SearchScope {
        val live = anchor ?: return GlobalSearchScope.EMPTY_SCOPE
        if (original.key.startsWith("v:_")) return LocalSearchScope(live.containingFile)
        val scope = project.getService(StoryScopeService::class.java).snapshot() ?: return GlobalSearchScope.EMPTY_SCOPE
        return if (scope.story.id == original.story) scope else GlobalSearchScope.EMPTY_SCOPE
    }
    override fun isEquivalentTo(another: PsiElement?) = another is TweeLogicalSymbol && identity()?.let { it == another.identity() } == true
    override fun toString() = "Twee symbol: $name"
}

object TweeSymbols {
    fun facts(file: TweeFile): List<RelationFact> = CachedValuesManager.getCachedValue(file) {
        CachedValueProvider.Result.create(RelationExtractor.extract(file), file)
    }
    fun factsAt(element: PsiElement): List<RelationFact> {
        val live = if (element is TweeLogicalSymbol) element.anchor ?: return emptyList() else element
        val file = live.containingFile as? TweeFile ?: return emptyList()
        val offset = live.textRange?.startOffset ?: return emptyList()
        return facts(file).filter { it.start <= offset && offset < it.end }
    }
    fun target(element: PsiElement): PsiElement? {
        if (element is TweeLogicalSymbol) return element.takeIf { it.isValid }
        if (element is TweePassage) return element
        val file = element.containingFile as? TweeFile ?: return null
        val offset = element.textRange?.startOffset ?: return null
        file.passages.firstOrNull { it.nameIdentifier?.textRange?.containsOffset(offset) == true }?.let { return it }
        val virtualFile = file.virtualFile ?: return null
        val scope = element.project.getService(StoryScopeService::class.java).snapshot() ?: return null
        if (!scope.contains(virtualFile) || !element.project.getService(StoryContextService::class.java).supportsHarlowe(virtualFile)) return null
        val fact = factsAt(element).firstOrNull { it.isVariable || it.kind == RelationFact.Kind.NAMED_CALL }
            ?: facts(file).firstOrNull { it.kind == RelationFact.Kind.MACRO_BINDING && it.body == offset }
            ?: return null
        val anchor = file.findElementAt(fact.start) ?: return null
        return TweeLogicalSymbol(anchor, RelationQueryService.Symbol(scope.story.id, fact.indexKey,
            if (fact.scope.isEmpty()) "" else virtualFile.url, fact.scope))
    }
}

class TweeTargetEvaluator : TargetElementEvaluatorEx2() {
    override fun getNamedElement(element: PsiElement): PsiElement? = TweeSymbols.target(element)
}
