package twee.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class BuildHtmlAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) { event.presentation.isEnabledAndVisible = event.project != null }
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val manager = RunManager.getInstance(project)
        val selected = manager.selectedConfiguration?.configuration as? TweegoRunConfiguration
        val configuration = if (selected != null) selected.clone() as TweegoRunConfiguration else {
            val factory = TweegoConfigurationType().configurationFactories.single()
            factory.createTemplateConfiguration(project) as TweegoRunConfiguration
        }
        configuration.name = "Build HTML: ${selected?.name ?: "selected story"}"
        configuration.buildOptions.openBrowser = false
        val settings = manager.createConfiguration(configuration, requireNotNull(configuration.factory))
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}
