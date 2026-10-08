package twee.relations

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.FileBasedIndex
import twee.parser.HarloweTypes
import twee.psi.TweeFile
import twee.scope.StoryContextService
import twee.scope.StoryScopeService

@Service(Service.Level.PROJECT)
class RelationQueryService(private val project: Project) {
    enum class State { READY, INDEXING, DOCUMENTS_UNCOMMITTED, NO_STORY, UNSUPPORTED }
    data class Symbol(val story: String, val key: String, val file: String = "", val scope: String = "")
    data class Occurrence(val fact: RelationFact, val symbol: Symbol, val fileUrl: String,
                          val pointer: SmartPsiElementPointer<PsiElement>)
    data class Result(val state: State, val occurrences: List<Occurrence> = emptyList())
    data class Bindings(val state: State, val candidates: List<SmartPsiElementPointer<PsiElement>>, val dynamic: Boolean = true)

    /** Persistent values only identify candidates; all returned occurrences come from committed live PSI. */
    fun query(key: String = RelationIndex.ALL): Result {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        val scope = project.getService(StoryScopeService::class.java).snapshot() ?: return Result(State.NO_STORY)
        if (DumbService.isDumb(project)) return Result(State.INDEXING)
        val context = project.getService(StoryContextService::class.java)
        if (context.current.storyId != scope.story.id || context.current.format != StoryContextService.Format.HARLOWE_3)
            return Result(State.UNSUPPORTED)
        val docs = PsiDocumentManager.getInstance(project)
        val fileDocs = FileDocumentManager.getInstance()
        fun eligible(file: com.intellij.openapi.vfs.VirtualFile) = scope.contains(file) && file.extension?.lowercase() in setOf("tw", "twee")
        if (docs.uncommittedDocuments.any { fileDocs.getFile(it)?.let(::eligible) == true }) return Result(State.DOCUMENTS_UNCOMMITTED)
        val files = try {
            FileBasedIndex.getInstance().getContainingFiles(RelationIndex.ID, key, scope).toMutableSet()
        } catch (_: IndexNotReadyException) { return Result(State.INDEXING) }
        fileDocs.unsavedDocuments.mapNotNull(fileDocs::getFile).filter(::eligible).forEach(files::add)
        val pointers = SmartPointerManager.getInstance(project)
        val occurrences = mutableListOf<Occurrence>()
        for (file in files.sortedBy { it.url }) {
            ProgressManager.checkCanceled()
            if (!file.isValid || !eligible(file)) continue
            val psi = PsiManager.getInstance(project).findFile(file) as? TweeFile ?: continue
            val facts = CachedValuesManager.getCachedValue(psi) { CachedValueProvider.Result.create(RelationExtractor.extract(psi), psi) }
            for (fact in facts) {
                ProgressManager.checkCanceled()
                if (key != RelationIndex.ALL && fact.indexKey != key) continue
                val element = psi.findElementAt(fact.start) ?: continue
                val symbol = Symbol(scope.story.id, fact.indexKey, if (fact.scope.isEmpty()) "" else file.url, fact.scope)
                occurrences.add(Occurrence(fact, symbol, file.url, pointers.createSmartPsiElementPointer(element)))
            }
        }
        return Result(State.READY, occurrences)
    }
    fun occurrences(symbol: Symbol): Result {
        val result = query(symbol.key)
        return result.copy(occurrences = result.occurrences.filter { it.symbol == symbol })
    }
    fun incomingPassage(name: String) = query("p:$name")
    fun outgoing(fileUrl: String, owner: Int): Result {
        val result = query()
        return result.copy(occurrences = result.occurrences.filter { it.fileUrl == fileUrl && it.fact.owner == owner })
    }
    fun reads(symbol: Symbol): Result = occurrences(symbol).let { result -> result.copy(occurrences = result.occurrences.filter { it.fact.reads }) }
    fun writes(symbol: Symbol): Result = occurrences(symbol).let { result -> result.copy(occurrences = result.occurrences.filter { it.fact.writes }) }
    /** No reaching-value claim: even one direct binding is only a source candidate. */
    fun customBindings(call: Occurrence): Bindings {
        if (call.fact.kind != RelationFact.Kind.CUSTOM_CALL) return Bindings(State.READY, emptyList())
        val result = occurrences(call.symbol)
        val pointers = SmartPointerManager.getInstance(project)
        val candidates = result.occurrences.filter { it.fact.kind == RelationFact.Kind.MACRO_BINDING }.mapNotNull {
            ProgressManager.checkCanceled()
            val file = it.pointer.element?.containingFile ?: return@mapNotNull null
            var element = file.findElementAt(it.fact.body)
            while (element != null && element.node?.elementType != HarloweTypes.HOOK) element = element.parent
            element?.let { body -> pointers.createSmartPsiElementPointer(body) }
        }.distinct()
        return Bindings(result.state, candidates)
    }
    fun queryAsync(key: String = RelationIndex.ALL) = ReadAction.nonBlocking<Result> { query(key) }
        .withDocumentsCommitted(project).inSmartMode(project).expireWith(project)
        .submit(AppExecutorUtil.getAppExecutorService())
}
