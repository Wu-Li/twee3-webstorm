package twee.scope

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.GridLayout
import java.util.UUID
import javax.swing.*

class StoryConfigurable(private val project: Project) : Configurable {
    private var panel: JPanel? = null
    private var draft = StorySettings.Options()
    private var editing: StorySettings.Story? = null
    private var changing = false
    private val stories = JComboBox<StorySettings.Story>()
    private val name = JTextField()
    private val roots = JTextArea(4, 40)
    private val exclusions = JTextArea(4, 40)
    private val output = JTextField()
    private val harloweOverride = JCheckBox("Use Harlowe 3 when StoryData is absent")
    private val settings get() = project.getService(StorySettings::class.java)
    override fun getDisplayName() = "Twee stories"
    private fun lines(area: JTextArea) = area.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    private fun save() { editing?.let { it.name = name.text.trim(); it.roots = lines(roots); it.exclusions = lines(exclusions); it.outputDirectory = output.text.trim(); it.harlowe3WhenMissing = harloweOverride.isSelected } }
    private fun show(story: StorySettings.Story?) {
        editing = story; name.text = story?.name ?: ""; roots.text = story?.roots?.joinToString("\n") ?: ""
        exclusions.text = story?.exclusions?.joinToString("\n") ?: ""; output.text = story?.outputDirectory ?: ""
        harloweOverride.isSelected = story?.harlowe3WhenMissing ?: false
    }
    private fun reload() {
        changing = true; stories.removeAllItems(); draft.stories.forEach(stories::addItem)
        stories.selectedItem = draft.stories.firstOrNull { it.id == draft.selectedStoryId }
        show(stories.selectedItem as? StorySettings.Story); changing = false
    }
    override fun createComponent(): JComponent {
        panel?.let { return it }
        val result = JPanel(BorderLayout())
        val top = JPanel(); top.add(JLabel("Selected story")); top.add(stories)
        top.add(JButton("Add").apply { addActionListener { save(); val story = StorySettings.Story(UUID.randomUUID().toString(), "New story"); draft.stories.add(story); draft.selectedStoryId = story.id; reload() } })
        top.add(JButton("Remove").apply { addActionListener { editing?.let { draft.stories.remove(it) }; draft.selectedStoryId = draft.stories.firstOrNull()?.id ?: ""; reload() } })
        val fields = JPanel(GridLayout(0, 1))
        for ((label, component) in listOf("Story name" to name, "Source directories or files (project-relative, one per line)" to JScrollPane(roots), "Excluded directories or files (one per line)" to JScrollPane(exclusions), "Output directory (excluded from sources)" to output)) {
            fields.add(JLabel(label)); fields.add(component)
        }
        fields.add(harloweOverride)
        val status = when (project.getService(StoryContextService::class.java).current.format) {
            StoryContextService.Format.HARLOWE_3 -> "Harlowe 3"
            StoryContextService.Format.MISSING -> "StoryData absent; choose the Harlowe 3 override if appropriate"
            StoryContextService.Format.UNSUPPORTED -> "StoryData does not select Harlowe 3"
            StoryContextService.Format.INVALID -> "StoryData JSON needs correction"
            StoryContextService.Format.AMBIGUOUS -> "Multiple StoryData passages; adjust the source paths or exclusions"
            StoryContextService.Format.NO_SELECTION -> "No story selected"
        }
        fields.add(JLabel("Last applied story format: $status"))
        result.add(top, BorderLayout.NORTH); result.add(fields, BorderLayout.CENTER)
        stories.addActionListener { if (!changing) { save(); val selected = stories.selectedItem as? StorySettings.Story; draft.selectedStoryId = selected?.id ?: ""; show(selected) } }
        panel = result; reset(); return result
    }
    override fun isModified(): Boolean { save(); return draft != settings.state }
    override fun apply() {
        save()
        for (story in draft.stories) {
            if (story.name.isBlank() || story.roots.isEmpty()) throw ConfigurationException("Each story needs a name and at least one source path.")
            if ((story.roots + story.exclusions + story.outputDirectory).any { StoryPaths.normalize(it) == null })
                throw ConfigurationException("Use project-relative paths that do not leave the project.")
        }
        settings.loadState(draft)
        project.getService(StoryContextService::class.java).settingsChanged()
    }
    override fun reset() { draft = settings.state; reload() }
    override fun disposeUIResources() { panel = null; editing = null; stories.actionListeners.forEach(stories::removeActionListener) }
}
