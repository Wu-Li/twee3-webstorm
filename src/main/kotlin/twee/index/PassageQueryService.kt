package twee.index

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.concurrency.AppExecutorUtil
import twee.psi.TweeFile
import twee.psi.TweePassage
import twee.scope.StoryScopeService

/** Index keys are file facts; story identity is applied only when querying. */
@Service(Service.Level.PROJECT)
class PassageQueryService(private val project: Project) {
    enum class State { READY, INDEXING, DOCUMENTS_UNCOMMITTED, NO_STORY }
    enum class Kind { NAME, TAG, ALL }
    data class Request(val kind: Kind, val key: String = "", val storyOverride: String? = null)
    data class Match(val storyId: String, val name: String, val tags: List<String>, val pointer: SmartPsiElementPointer<TweePassage>)
    data class Result(val state: State, val passages: List<Match> = emptyList())

    /** Caller holds a read action. Does not commit documents, wait for indexes or retain PSI trees. */
    fun query(request: Request): Result {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        val scope = project.getService(StoryScopeService::class.java).snapshot(request.storyOverride) ?: return Result(State.NO_STORY)
        if (DumbService.isDumb(project)) return Result(State.INDEXING)
        val documents = PsiDocumentManager.getInstance(project)
        val fileDocuments = FileDocumentManager.getInstance()
        fun eligible(file: VirtualFile) = file.extension?.lowercase() in setOf("tw", "twee") && scope.contains(file)
        if (documents.uncommittedDocuments.any { document -> fileDocuments.getFile(document)?.let(::eligible) == true })
            return Result(State.DOCUMENTS_UNCOMMITTED)
        val index = when (request.kind) {
            Kind.NAME -> TweePassageNameIndex.KEY
            Kind.TAG -> TweePassageTagIndex.KEY
            Kind.ALL -> TweeAllPassagesIndex.KEY
        }
        val key = if (request.kind == Kind.ALL) "all" else request.key
        // Complete index access before traversing any live PSI: no nested index callbacks.
        val files = linkedSetOf<VirtualFile>()
        try {
            for (candidate in StubIndex.getElements(index, key, project, scope, TweePassage::class.java)) {
                ProgressManager.checkCanceled()
                candidate.containingFile.virtualFile?.let(files::add)
            }
        } catch (_: IndexNotReadyException) { return Result(State.INDEXING) }
        // Newly added/renamed declarations in committed, unsaved documents may not be on disk yet.
        for (document in fileDocuments.unsavedDocuments) {
            ProgressManager.checkCanceled()
            fileDocuments.getFile(document)?.takeIf(::eligible)?.let(files::add)
        }
        val pointers = SmartPointerManager.getInstance(project)
        val result = mutableListOf<Match>()
        for (file in files.sortedBy { it.url }) {
            ProgressManager.checkCanceled()
            if (!file.isValid || !scope.contains(file)) continue
            val live = PsiManager.getInstance(project).findFile(file) as? TweeFile ?: continue
            for (passage in live.passages) {
                ProgressManager.checkCanceled()
                val matches = when (request.kind) {
                    Kind.NAME -> passage.name == request.key
                    Kind.TAG -> request.key in passage.tags
                    Kind.ALL -> true
                }
                if (matches) result.add(Match(scope.story.id, passage.name, passage.tags.toList(), pointers.createSmartPsiElementPointer(passage)))
            }
        }
        return Result(State.READY, result)
    }
    /** Preferred UI entry point: cancellable, committed, smart-mode background read. */
    fun queryAsync(request: Request) = ReadAction.nonBlocking<Result> { query(request) }
        .withDocumentsCommitted(project).inSmartMode(project).expireWith(project)
        .submit(AppExecutorUtil.getAppExecutorService())
}
