package twee.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import twee.scope.StoryPaths
import twee.scope.StorySettings

class TweegoRunOptions : RunConfigurationOptions() {
    var storyId: String? by string()
    var workingDirectory: String? by string()
    var outputPath: String? by string("dist/index.html")
    var formatId: String? by string()
    var environmentText: String? by string()
    var browserId: String? by string()
    var openBrowser: Boolean by property(true)

    fun request() = TweegoBuildService.Request(storyId?.takeIf { it.isNotBlank() },
        workingDirectory?.takeIf { it.isNotBlank() }, outputPath.orEmpty(), formatId?.takeIf { it.isNotBlank() },
        parseEnvironment(environmentText.orEmpty()))

    companion object {
        /** First '=' separates key/value; keep spaces, '=' and shell characters in the value literally. */
        fun parseEnvironment(text: String): Map<String, String> {
            val result = linkedMapOf<String, String>()
            for (line in text.lineSequence().filter { it.isNotBlank() }) {
                val equals = line.indexOf('=')
                require(equals > 0) { "Environment entries must be NAME=value, one per line." }
                val name = line.substring(0, equals).trim()
                require(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name)) { "Invalid environment variable name: $name" }
                require(name !in result) { "Duplicate environment variable: $name" }
                val value = line.substring(equals + 1)
                require('\u0000' !in value) { "Environment values cannot contain NUL." }
                result[name] = value
            }
            return result
        }
    }
}

class TweegoConfigurationType : ConfigurationType {
    private val factory = TweegoConfigurationFactory(this)
    override fun getDisplayName() = "Tweego"
    override fun getConfigurationTypeDescription() = "Build a Twee story and open Harlowe 3 HTML"
    override fun getIcon() = AllIcons.RunConfigurations.Application
    override fun getId() = "TweeTweego"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}

class TweegoConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId() = "Tweego"
    override fun getOptionsClass() = TweegoRunOptions::class.java
    override fun createTemplateConfiguration(project: Project) = TweegoRunConfiguration(project, this, "Tweego")
}

class TweegoRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<TweegoRunOptions>(project, factory, name) {
    val buildOptions get() = super.getOptions() as TweegoRunOptions
    override fun getConfigurationEditor() = TweegoSettingsEditor(project)
    override fun checkConfiguration() {
        val settings = project.getService(StorySettings::class.java).state
        val id = buildOptions.storyId?.takeIf { it.isNotBlank() } ?: settings.selectedStoryId
        if (settings.stories.count { it.id == id } != 1) throw RuntimeConfigurationError("Select a named story in Settings | Twee stories.")
        val directory = buildOptions.workingDirectory
        if (!directory.isNullOrBlank() && StoryPaths.normalize(directory) == null)
            throw RuntimeConfigurationError("Working directory must be project-relative.")
        if (buildOptions.outputPath.isNullOrBlank() || buildOptions.outputPath!!.substringAfterLast('.').lowercase() !in setOf("html", "htm"))
            throw RuntimeConfigurationError("Choose an .html or .htm output path.")
        try { buildOptions.request() } catch (error: IllegalArgumentException) { throw RuntimeConfigurationError(error.message.orEmpty()) }
    }
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        if (executor.id != DefaultRunExecutor.EXECUTOR_ID) return null
        val request = buildOptions.request().let { request ->
            if (request.storyId != null) request else request.copy(
                storyId = project.getService(StorySettings::class.java).state.selectedStoryId)
        }
        val launch = buildOptions.openBrowser
        val browser = buildOptions.browserId
        return object : CommandLineState(environment) {
            init { addConsoleFilters(TweegoConsoleFilter(project, request.storyId)) }
            override fun startProcess() = TweegoRunProcess(project, request, launch, browser)
        }
    }
}
