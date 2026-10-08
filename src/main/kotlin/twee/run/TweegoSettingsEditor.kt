package twee.run

import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.ui.FormBuilder
import twee.scope.StorySettings
import javax.swing.*

class TweegoSettingsEditor(private val project: Project) : SettingsEditor<TweegoRunConfiguration>() {
    private data class Choice(val id: String?, val label: String) { override fun toString() = label }
    private val story = JComboBox<Choice>()
    private val directory = JTextField()
    private val output = JTextField()
    private val format = JTextField()
    private val environment = JTextArea(5, 45)
    private val browser = JComboBox<Choice>()
    private val launch = JCheckBox("Open browser after successful build")
    private val panel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Story:", story)
        .addLabeledComponent("Working directory (blank = story root):", directory)
        .addLabeledComponent("Output HTML (relative to working directory):", output)
        .addLabeledComponent("Installed format ID (blank = automatic Harlowe 3):", format)
        .addLabeledComponent("Environment overrides (NAME=value per line):", JScrollPane(environment))
        .addComponent(launch).addLabeledComponent("Browser:", browser)
        .addComponent(JLabel("Configure the local compiler and format directories in Settings | Twee stories."))
        .addComponent(JLabel("Keep relative assets beside the output HTML; assets are not copied."))
        .panel

    override fun createEditor(): JComponent = panel
    override fun resetEditorFrom(configuration: TweegoRunConfiguration) {
        val options = configuration.buildOptions
        story.removeAllItems(); story.addItem(Choice(null, "Follow selected story"))
        project.getService(StorySettings::class.java).state.stories.forEach { story.addItem(Choice(it.id, it.name)) }
        select(story, options.storyId, "Missing story")
        directory.text = options.workingDirectory.orEmpty(); output.text = options.outputPath.orEmpty()
        format.text = options.formatId.orEmpty(); environment.text = options.environmentText.orEmpty()
        browser.removeAllItems(); browser.addItem(Choice(null, "IDE default browser"))
        WebBrowserManager.getInstance().activeBrowsers.forEach { browser.addItem(Choice(it.id.toString(), it.name)) }
        select(browser, options.browserId, "Unavailable browser")
        launch.isSelected = options.openBrowser
    }
    private fun select(combo: JComboBox<Choice>, id: String?, missing: String) {
        val wanted = id?.takeIf { it.isNotBlank() }
        val existing = (0 until combo.itemCount).map { combo.getItemAt(it) }.firstOrNull { it.id == wanted }
        val choice = existing ?: Choice(wanted, "$missing: $wanted").also { combo.addItem(it) }
        combo.selectedItem = choice
    }
    override fun applyEditorTo(configuration: TweegoRunConfiguration) {
        try { TweegoRunOptions.parseEnvironment(environment.text) }
        catch (error: IllegalArgumentException) { throw ConfigurationException(error.message.orEmpty()) }
        configuration.buildOptions.apply {
            storyId = (story.selectedItem as? Choice)?.id
            workingDirectory = directory.text.trim().ifEmpty { null }
            outputPath = output.text.trim()
            formatId = format.text.trim().ifEmpty { null }
            environmentText = environment.text
            browserId = (browser.selectedItem as? Choice)?.id
            openBrowser = launch.isSelected
        }
    }
}
