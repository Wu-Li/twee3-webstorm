package twee.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class TweeChecksConfigurable(private val project: Project) : Configurable {
    private var panel: JPanel? = null
    private val spaceAfterStartToken = JCheckBox("Warn about missing whitespace after ::")
    private val ifid = JCheckBox("Notify about missing or invalid StoryData IFID")
    private val format = JCheckBox("Notify about missing StoryData format")
    private val formatVersion = JCheckBox("Notify about missing StoryData format-version")
    private val settings get() = project.getService(TweeCheckSettings::class.java)
    override fun getDisplayName() = "Twee checks"
    override fun createComponent(): JComponent {
        return panel ?: JPanel().also {
            it.layout = BoxLayout(it, BoxLayout.Y_AXIS)
            listOf(spaceAfterStartToken, ifid, format, formatVersion).forEach(it::add)
            panel = it; reset()
        }
    }
    private fun selected() = TweeCheckSettings.Options(spaceAfterStartToken.isSelected, ifid.isSelected, format.isSelected, formatVersion.isSelected)
    override fun isModified() = selected() != settings.state
    override fun apply() {
        settings.loadState(selected())
        project.getService(twee.scope.StoryContextService::class.java).settingsChanged()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
    override fun reset() {
        val value = settings.state
        spaceAfterStartToken.isSelected = value.spaceAfterStartToken
        ifid.isSelected = value.ifid
        format.isSelected = value.format
        formatVersion.isSelected = value.formatVersion
    }
    override fun disposeUIResources() { panel = null }
}
