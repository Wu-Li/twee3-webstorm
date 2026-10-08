package twee.scope

import com.google.gson.JsonParser
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import twee.inspections.StoryDataChecks
import twee.inspections.StoryDataNotifications
import twee.parser.TweeJson
import twee.psi.TweeFile
import twee.settings.TweeCheckSettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.PROJECT)
class StoryContextService(private val project: Project) : Disposable {
    enum class Format { HARLOWE_3, MISSING, UNSUPPORTED, INVALID, AMBIGUOUS, NO_SELECTION }
    data class Snapshot(val storyId: String?, val format: Format, val source: VirtualFile? = null,
                        val messages: List<String> = emptyList(), val declaredFormat: String? = null,
                        val declaredVersion: String? = null)
    private data class Cached(val diskStamp: Long, val documentStamp: Long, val bodies: List<String>)
    private val parsed = ConcurrentHashMap<VirtualFile, Cached>()
    private val revision = AtomicLong()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    @Volatile var current = Snapshot(null, Format.NO_SELECTION)
        private set
    private data class Candidates(val storyId: String, val fingerprint: Int, val treeRevision: Long, val files: List<VirtualFile>)
    @Volatile private var refreshHighlighters = true
    private val treeRevision = AtomicLong()
    @Volatile private var candidates: Candidates? = null
    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (file.extension?.lowercase() in setOf("tw", "twee")) requestRefresh()
            }
        }, this)
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.isNotEmpty()) {
                    if (events.any { it !is com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent }) {
                        treeRevision.incrementAndGet(); candidates = null; refreshHighlighters = true
                    }
                    requestRefresh()
                }
            }
        })
    }
    fun settingsChanged() {
        refreshHighlighters = true
        candidates = null
        project.getService(StoryDataNotifications::class.java).refreshSettings()
        requestRefresh()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
    fun requestRefresh() {
        val requested = revision.incrementAndGet()
        alarm.cancelAllRequests()
        alarm.addRequest({
            if (!project.isDisposed) ReadAction.nonBlocking<Snapshot> {
                val scope = project.getService(StoryScopeService::class.java).snapshot()
                compute(scope)
            }.withDocumentsCommitted(project).expireWith(project).coalesceBy(this)
                .finishOnUiThread(ModalityState.nonModal()) { snapshot ->
                    if (requested == revision.get()) publish(snapshot, requested)
                }.submit(AppExecutorUtil.getAppExecutorService())
        }, 250)
    }
    /** Caller holds a read action with committed documents. Only changed files are reparsed. */
    internal fun compute(scope: StoryScope?): Snapshot {
        if (scope == null) return Snapshot(null, Format.NO_SELECTION)
        val tree = treeRevision.get()
        val files = candidates?.takeIf { it.storyId == scope.story.id && it.fingerprint == scope.fingerprint && it.treeRevision == tree }?.files
            ?: scope.files().filter { it.extension?.lowercase() in setOf("tw", "twee") }.also {
                candidates = Candidates(scope.story.id, scope.fingerprint, tree, it)
            }
        val fileSet = files.toHashSet()
        parsed.keys.removeIf { !it.isValid || it !in fileSet }
        val found = mutableListOf<Pair<VirtualFile, String>>()
        for (file in files) {
            ProgressManager.checkCanceled()
            val stamp = FileDocumentManager.getInstance().getCachedDocument(file)?.modificationStamp ?: -1
            val cached = parsed[file]?.takeIf { it.diskStamp == file.modificationStamp && it.documentStamp == stamp }
                ?: run {
                    val psi = PsiManager.getInstance(project).findFile(file) as? TweeFile
                    Cached(file.modificationStamp, stamp, psi?.passages?.filter { it.name == "StoryData" }
                        ?.map { it.bodyRange.substring(psi.text) } ?: emptyList()).also { parsed[file] = it }
                }
            found.addAll(cached.bodies.map { file to it })
        }
        if (found.size > 1) return Snapshot(scope.story.id, Format.AMBIGUOUS)
        if (found.isEmpty()) return Snapshot(scope.story.id, if (scope.story.harlowe3WhenMissing) Format.HARLOWE_3 else Format.MISSING)
        val (file, text) = found.single()
        val checks = StoryDataChecks.evaluate(text, project.getService(TweeCheckSettings::class.java).state)
        if (!TweeJson.valid(text)) return Snapshot(scope.story.id, Format.INVALID, file, checks.messages)
        val value = JsonParser.parseString(text)
        fun string(name: String): String? = value.takeIf { it.isJsonObject }?.asJsonObject?.get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val format = string("format")
        val version = string("format-version")
        val supported = format.equals("Harlowe", true) && version?.substringBefore('.') == "3"
        return Snapshot(scope.story.id, if (supported) Format.HARLOWE_3 else Format.UNSUPPORTED, file, checks.messages, format, version)
    }
    private fun publish(snapshot: Snapshot, stamp: Long) {
        val before = current; current = snapshot
        if (before != snapshot) project.messageBus.syncPublisher(StoryContextListener.TOPIC).changed()
        val notices = project.getService(StoryDataNotifications::class.java)
        notices.retainOnly(snapshot.source)
        snapshot.source?.let { notices.update(it, snapshot.messages, stamp) }
        if (refreshHighlighters || before.format != snapshot.format || before.storyId != snapshot.storyId) {
            refreshHighlighters = false
            val factory = com.intellij.openapi.editor.highlighter.EditorHighlighterFactory.getInstance()
            for (editor in EditorFactory.getInstance().allEditors) {
                if (editor.project != project || editor !is com.intellij.openapi.editor.ex.EditorEx) continue
                val file = FileDocumentManager.getInstance().getFile(editor.document) ?: continue
                if (file.extension?.lowercase() in setOf("tw", "twee")) editor.highlighter = factory.createEditorHighlighter(project, file)
            }
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
    fun supportsHarlowe(file: VirtualFile?): Boolean {
        // Scratch/color-demo files have no story identity; preserve Harlowe editing there.
        if (file == null || file.fileSystem.protocol != "file") return true
        val scope = project.getService(StoryScopeService::class.java).snapshot() ?: return false
        return current.storyId == scope.story.id && current.format == Format.HARLOWE_3 && scope.contains(file)
    }
    override fun dispose() { revision.incrementAndGet(); candidates = null; parsed.clear() }
}

class StoryStartup : ProjectActivity {
    override suspend fun execute(project: Project) { project.getService(StoryContextService::class.java).requestRefresh() }
}

interface StoryContextListener {
    fun changed()
    companion object { val TOPIC = com.intellij.util.messages.Topic.create("Twee story context", StoryContextListener::class.java) }
}
