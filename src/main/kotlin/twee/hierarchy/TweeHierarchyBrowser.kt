package twee.hierarchy

import com.intellij.ide.hierarchy.*
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.*
import com.intellij.util.Alarm
import twee.psi.TweeFile
import twee.scope.StorySettingsListener
import twee.scope.StoryContextListener
import java.util.Comparator
import javax.swing.*

class TweeHierarchyProvider : HierarchyProvider {
    override fun getTarget(dataContext: DataContext): PsiElement? {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null
        val model = TweeHierarchyModel(project)
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        if (editor != null) {
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? TweeFile ?: return null
            val offset = editor.caretModel.offset
            val leaf = file.findElementAt(offset) ?: file.findElementAt((offset - 1).coerceAtLeast(0)) ?: return null
            return model.at(leaf, offset)?.element
        }
        return CommonDataKeys.PSI_ELEMENT.getData(dataContext)?.let { model.at(it)?.element }
    }
    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser = TweeHierarchyBrowser(target.project, target)
    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) { (hierarchyBrowser as TweeHierarchyBrowser).activateDefault() }
}

class TweeHierarchyBrowser(private val ownerProject: Project, element: PsiElement) : HierarchyBrowserBaseEx(ownerProject, element) {
    private val kind = TweeHierarchyModel(ownerProject).at(element)?.kind ?: TweeHierarchyModel.Kind.SYMBOL
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    @Volatile private var stopped = false
    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (FileDocumentManager.getInstance().getFile(event.document)?.extension?.lowercase() in setOf("tw", "twee")) refreshLater()
            }
        }, this)
        val connection = ownerProject.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) { if (events.isNotEmpty()) refreshLater() }
        })
        connection.subscribe(StorySettingsListener.TOPIC, object : StorySettingsListener { override fun changed() { refreshLater() } })
        connection.subscribe(StoryContextListener.TOPIC, object : StoryContextListener { override fun changed() { refreshLater() } })
        connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun enteredDumbMode() { refreshLater() }
            override fun exitDumbMode() { refreshLater() }
        })
    }
    fun activateDefault() {
        val element = hierarchyBase ?: return
        val target = TweeHierarchyModel.Target(kind, SmartPointerManager.getInstance(ownerProject).createSmartPsiElementPointer(element))
        changeView(TweeHierarchyModel(ownerProject).defaultView(target).title)
    }
    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        for (view in TweeHierarchyModel.View.entries) trees[view.title] = createTree(false)
    }
    override fun createLegendPanel() = JPanel().apply { add(JLabel("Static source relations · candidates are not runtime order · expand rows for source sites")) }
    override fun isApplicableElement(element: PsiElement) = element.isValid && element.containingFile is TweeFile
    override fun getActionPlace() = "TweeHierarchy"
    override fun getPrevOccurenceActionNameImpl() = "Previous source occurrence"
    override fun getNextOccurenceActionNameImpl() = "Next source occurrence"
    override fun getComparator(): Comparator<NodeDescriptor<*>>? = null
    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? = descriptor.psiElement
    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure {
        val target = TweeHierarchyModel.Target(kind, SmartPointerManager.getInstance(ownerProject).createSmartPsiElementPointer(psiElement))
        return TweeHierarchyTree(ownerProject, target, TweeHierarchyModel.View.entries.first { it.title == type })
    }
    override fun prependActions(actionGroup: DefaultActionGroup) {
        for (view in TweeHierarchyModel.View.entries) actionGroup.add(object : ToggleAction(view.title) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun isSelected(e: AnActionEvent) = currentViewType == view.title
            override fun setSelected(e: AnActionEvent, state: Boolean) { if (state) changeView(view.title) }
        })
    }
    private fun refreshLater() {
        ToolWindowManager.getInstance(ownerProject).invokeLater {
            if (!stopped && !ownerProject.isDisposed) {
                alarm.cancelAllRequests()
                alarm.addRequest({ if (!stopped) doRefresh(false) }, 250)
            }
        }
    }
    override fun dispose() { stopped = true; alarm.cancelAllRequests(); super.dispose() }
}

class TweeHierarchyDescriptor(
    project: Project, parent: TweeHierarchyDescriptor?, element: PsiElement,
    val target: TweeHierarchyModel.Target?, val label: String,
    val sites: List<SmartPsiElementPointer<PsiElement>> = emptyList(),
    val ancestors: Set<String> = emptySet(), val terminal: Boolean = false
) : HierarchyNodeDescriptor(project, parent, element, parent == null) {
    override fun update(): Boolean {
        super.update()
        myName = label + (target?.let { " · ${it.label()}" } ?: "")
        myHighlightedText = CompositeAppearance()
        myHighlightedText.ending.addText(myName)
        return true
    }
}

class TweeHierarchyTree(project: Project, root: TweeHierarchyModel.Target, private val view: TweeHierarchyModel.View) :
    HierarchyTreeStructure(project, TweeHierarchyDescriptor(project, null, root.element!!, root, view.title, ancestors = setOf(root.key()))) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        ProgressManager.checkCanceled()
        val node = descriptor as TweeHierarchyDescriptor
        val element = node.psiElement ?: return emptyArray()
        val children = mutableListOf<Any>()
        for (site in node.sites) {
            ProgressManager.checkCanceled()
            val source = site.element ?: continue
            val document = PsiDocumentManager.getInstance(myProject).getDocument(source.containingFile)
            val line = document?.getLineNumber(source.textRange.startOffset)?.plus(1)
            children.add(TweeHierarchyDescriptor(myProject, node, source, null,
                "Source: ${source.containingFile.name}:${line ?: source.textRange.startOffset}", terminal = true))
        }
        if (!node.terminal && node.target != null) {
            val result = TweeHierarchyModel(myProject).children(node.target, view)
            result.status?.let { children.add(TweeHierarchyDescriptor(myProject, node, element, null, it, terminal = true)) }
            for (row in result.rows) {
                ProgressManager.checkCanceled()
                val key = row.target?.key()
                val cycle = key != null && key in node.ancestors
                val destination = row.target?.element ?: row.sites.firstOrNull()?.element ?: element
                children.add(TweeHierarchyDescriptor(myProject, node, destination, row.target,
                    row.label + if (cycle) " · cycle" else "", row.sites,
                    if (key == null) node.ancestors else node.ancestors + key, cycle || row.target == null))
            }
        }
        return children.toTypedArray()
    }
}
