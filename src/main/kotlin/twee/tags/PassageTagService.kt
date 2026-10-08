package twee.tags

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPsiElementPointer
import twee.psi.TweePassage
import twee.scope.StoryScopeService

@Service(Service.Level.PROJECT)
class PassageTagService(private val project: Project) {
    /** One command for every selected file. Validate the entire batch before touching text. */
    fun edit(pointers: List<SmartPsiElementPointer<TweePassage>>, tag: String, operation: TagEdits.Operation): Int {
        ApplicationManager.getApplication().assertIsDispatchThread()
        require(TagEdits.valid(tag)) { "Enter one nonempty tag without whitespace or unescaped brackets/braces." }
        if (pointers.isEmpty()) return 0
        val documents = PsiDocumentManager.getInstance(project)
        documents.commitAllDocuments()
        val files = pointers.map { requireNotNull(it.element?.containingFile) { "A selected passage no longer exists." } }.distinct()
        require(!ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files.map { requireNotNull(it.virtualFile) { "A selected passage has no physical file." } }).hasReadonlyFiles()) {
            "Some selected files are read-only. No tags were changed."
        }
        var count = 0
        WriteCommandAction.writeCommandAction(project, *files.toTypedArray())
            .withName(if (operation == TagEdits.Operation.ADD) "Add passage tag" else "Remove passage tag")
            .withGlobalUndo().run<RuntimeException> {
                documents.commitAllDocuments()
                val scope = requireNotNull(project.getService(StoryScopeService::class.java).snapshot()) { "Select a story first." }
                val passages = pointers.map { requireNotNull(it.element) { "A selected passage no longer exists." } }
                    .distinctBy { it.containingFile.virtualFile to it.textRange.startOffset }
                val batches = linkedMapOf<Document, MutableList<TagEdits.Edit>>()
                for (passage in passages) {
                    require(passage.isValid && passage.containingFile.virtualFile?.let(scope::contains) == true) { "A selected passage is outside the selected story. Refresh and try again." }
                    val document = requireNotNull(documents.getDocument(passage.containingFile))
                    require(document.isWritable) { "A selected document is read-only." }
                    val header = passage.headerRange
                    val edits = TagEdits.plan(document.getText(header), tag, operation).map {
                        TagEdits.Edit(it.range.shiftRight(header.startOffset), it.replacement)
                    }
                    for (edit in edits) {
                        require(document.getRangeGuard(edit.range.startOffset, edit.range.endOffset) == null &&
                            document.getOffsetGuard(edit.range.startOffset) == null) { "A selected tag is in a protected region." }
                    }
                    if (edits.isNotEmpty()) { count++; batches.getOrPut(document) { mutableListOf() }.addAll(edits) }
                }
                // Nothing may invalidate offsets between planning and application in this write action.
                for ((document, edits) in batches) {
                    for (edit in edits.sortedByDescending { it.range.startOffset })
                        document.replaceString(edit.range.startOffset, edit.range.endOffset, edit.replacement)
                    documents.commitDocument(document)
                }
            }
        return count
    }
}
