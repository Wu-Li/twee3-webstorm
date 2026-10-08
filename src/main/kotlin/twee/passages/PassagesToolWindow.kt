package twee.passages

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import twee.index.PassageQueryService
import twee.scope.StorySettings
import twee.scope.StorySettingsListener
import twee.tags.PassageTagService
import twee.tags.TagEdits
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class PassagesToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PassagesPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

class PassagesPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private data class Row(val match: PassageQueryService.Match) {
        override fun toString() = "${match.name} — ${match.pointer.virtualFile?.presentableUrl.orEmpty()}"
    }
    private val root = DefaultMutableTreeNode("Passages")
    private val tree = Tree(DefaultTreeModel(root))
    private val status = JBLabel("Loading passages…")
    private val selection = JBLabel("Select passages to edit tags")
    private val tag = JComboBox<String>().apply { isEditable = true }
    private val add = JButton("Add")
    private val remove = JButton("Remove")
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    @Volatile private var disposed = false
    private var revision = 0L
    private var rebuilding = false

    init {
        tree.isRootVisible = false
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        val refresh = JButton("Refresh").apply { addActionListener { requestRefresh() } }
        add(JPanel(BorderLayout()).apply { add(status, BorderLayout.CENTER); add(refresh, BorderLayout.EAST) }, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        add(JPanel(GridLayout(0, 1)).apply {
            add(selection)
            add(JPanel(BorderLayout()).apply {
                add(tag, BorderLayout.CENTER)
                add(JPanel().apply { add(this@PassagesPanel.add); add(remove) }, BorderLayout.EAST)
            })
        }, BorderLayout.SOUTH)
        add.isEnabled = false; remove.isEnabled = false
        add.addActionListener { edit(TagEdits.Operation.ADD) }
        remove.addActionListener { edit(TagEdits.Operation.REMOVE) }
        tree.addTreeSelectionListener {
            if (!rebuilding) {
                updateSelection()
                if (selected().size == 1) navigate(false)
            }
        }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) { if (event.clickCount == 2) navigate(true) }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) { if (event.keyCode == KeyEvent.VK_ENTER) navigate(true) }
        })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (file.extension?.lowercase() in setOf("tw", "twee")) requestRefresh()
            }
        }, this)
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) { if (events.isNotEmpty()) requestRefresh() }
        })
        connection.subscribe(StorySettingsListener.TOPIC, object : StorySettingsListener {
            override fun changed() { requestRefresh() }
        })
        connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun enteredDumbMode() { requestRefresh() }
            override fun exitDumbMode() { requestRefresh() }
        })
        requestRefresh()
    }

    private fun selected(): List<PassageQueryService.Match> = tree.selectionPaths.orEmpty().mapNotNull {
        ((it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? Row)?.match
    }.distinctBy { it.pointer }

    private fun updateSelection() {
        val rows = selected()
        val state = PassageGroups.selection(rows.map { it.tags })
        selection.text = if (rows.isEmpty()) "Select passages to edit tags" else
            "${rows.size} selected · All: ${state.all.joinToString().ifEmpty { "none" }} · Some: ${state.some.joinToString().ifEmpty { "none" }}"
        add.isEnabled = rows.isNotEmpty(); remove.isEnabled = rows.isNotEmpty()
    }

    private fun navigate(focus: Boolean) {
        val passage = selected().singleOrNull()?.pointer?.element ?: return
        val file = passage.containingFile.virtualFile ?: return
        OpenFileDescriptor(project, file, passage.textOffset).navigate(focus)
    }

    private fun edit(operation: TagEdits.Operation) {
        try {
            val count = project.getService(PassageTagService::class.java).edit(selected().map { it.pointer }, tag.editor.item?.toString().orEmpty(), operation)
            status.text = if (count == 0) "No changes needed" else "Updated $count passage(s)"
            requestRefresh()
        } catch (error: IllegalArgumentException) {
            status.text = error.message ?: "Unable to edit tags"
        }
    }

    private fun requestRefresh() {
        ToolWindowManager.getInstance(project).invokeLater {
            if (!disposed && !project.isDisposed) {
                revision++
                alarm.cancelAllRequests()
                alarm.addRequest({ refresh(revision) }, 150)
            }
        }
    }

    private fun refresh(expected: Long) {
        if (disposed || project.isDisposed) return
        val story = project.getService(StorySettings::class.java).state.selectedStoryId
        status.text = if (DumbService.isDumb(project)) "Indexing passages…" else "Refreshing passages…"
        add.isEnabled = false; remove.isEnabled = false
        ReadAction.nonBlocking<PassageQueryService.Result> {
            project.getService(PassageQueryService::class.java).query(PassageQueryService.Request(PassageQueryService.Kind.ALL))
        }.withDocumentsCommitted(project).inSmartMode(project).expireWith(this).coalesceBy(this)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.nonModal()) { result ->
                if (!disposed && expected == revision && story == project.getService(StorySettings::class.java).state.selectedStoryId)
                    render(result)
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun render(result: PassageQueryService.Result) {
        val selectedPointers = selected().map { it.pointer }.toSet()
        val expanded = (0 until tree.rowCount).mapNotNull { i ->
            tree.getPathForRow(i)?.takeIf { tree.isExpanded(it) }?.lastPathComponent?.toString()
        }.toSet()
        val first = root.childCount == 0
        val restore = mutableListOf<TreePath>()
        rebuilding = true
        try {
            root.removeAllChildren()
            for ((group, rows) in PassageGroups.group(result.passages)) {
                val node = DefaultMutableTreeNode(if (group == null) "Untagged" else "Tag: $group")
                root.add(node)
                for (row in rows) {
                    val child = DefaultMutableTreeNode(Row(row)); node.add(child)
                    if (row.pointer in selectedPointers) restore.add(TreePath(child.path))
                }
            }
            (tree.model as DefaultTreeModel).reload()
            for (i in 0 until root.childCount) {
                val node = root.getChildAt(i) as DefaultMutableTreeNode
                if (first || node.toString() in expanded) tree.expandPath(TreePath(node.path))
            }
            tree.selectionPaths = restore.toTypedArray()
            val input = tag.editor.item
            tag.model = DefaultComboBoxModel(result.passages.flatMap { it.tags }.distinct().sorted().toTypedArray())
            tag.editor.item = input
        } finally { rebuilding = false }
        status.text = when (result.state) {
            PassageQueryService.State.READY -> "${result.passages.size} passages"
            PassageQueryService.State.NO_STORY -> "Select a story in Settings → Languages & Frameworks → Twee stories"
            PassageQueryService.State.INDEXING -> "Indexing passages…"
            PassageQueryService.State.DOCUMENTS_UNCOMMITTED -> "Waiting for document changes…"
        }
        updateSelection()
    }

    override fun dispose() { disposed = true; revision++; alarm.cancelAllRequests() }
}
