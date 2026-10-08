package twee.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.EnvironmentUtil
import twee.scope.StoryContextService
import twee.scope.StoryLocalTools
import twee.scope.StoryPaths
import twee.scope.StoryScope
import twee.scope.StoryScopeService
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Shared entry point for Build HTML and the run configuration. No browser side effects. */
@Service(Service.Level.PROJECT)
class TweegoBuildService(private val project: Project) {
    data class Request(val storyId: String? = null, val workingDirectory: String? = null,
                       val outputPath: String = "dist/index.html", val formatId: String? = null,
                       val environment: Map<String, String> = emptyMap())
    data class Result(val output: Path? = null, val formatId: String? = null, val formatVersion: String? = null,
                      val error: String? = null, val canceled: Boolean = false) {
        val successful get() = output != null && error == null && !canceled
    }

    /** Output is streamed on process threads; completion is delivered on the UI thread. */
    fun start(request: Request, output: (String, Boolean) -> Unit, completed: (Result) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Build Twee HTML", true) {
            private var result = Result(canceled = true)
            override fun run(indicator: ProgressIndicator) { result = build(request, indicator, output) }
            override fun onSuccess() { completed(result) }
            override fun onCancel() { completed(Result(canceled = true)) }
        })
    }

    /** Blocking entry point for execution adapters; caller must be off the EDT and outside a read action. */
    fun build(request: Request, indicator: ProgressIndicator, output: (String, Boolean) -> Unit): Result {
        check(!ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isReadAccessAllowed)
        return try {
            indicator.checkCanceled()
            check(!project.isDisposed) { "Project is closed." }
            val base = Path.of(requireNotNull(project.basePath) { "Open a project before building." }).toRealPath()
            val local = project.getService(StoryLocalTools::class.java).state
            val initial = ReadAction.compute<StoryScope, RuntimeException> {
                requireNotNull(project.getService(StoryScopeService::class.java).snapshot(request.storyId)) { "Select a valid named story in Twee settings." }
            }
            val root = request.workingDirectory ?: initial.story.roots.firstOrNull()
                ?: error("The selected story has no input roots.")
            val relative = requireNotNull(StoryPaths.normalize(root)) { "Working directory must be project-relative." }
            val candidate = base.resolve(relative)
            val directory = (if (request.workingDirectory == null && Files.isRegularFile(candidate)) candidate.parent else candidate).toRealPath()
            require(directory.startsWith(base) && Files.isDirectory(directory)) { "Working directory must be inside the project." }
            val environment = TweegoBuildEngine.environment(EnvironmentUtil.getEnvironmentMap(), local.formatDirectories, request.environment)
            val executable = TweegoBuildEngine.executable(local.compilerPath, environment, directory)
            // Exclude resolved PATH compiler and every implicit/explicit format installation, too.
            val formatRoots = environment["TWEEGO_PATH"].orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
                .map { directory.resolve(it).normalize().toString() }
            val defaults = listOfNotNull(executable.parent, environment["HOME"]?.let { Path.of(it) },
                environment["USERPROFILE"]?.let { Path.of(it) }, directory).flatMap { parent ->
                listOf("storyformats", ".storyformats", "story-formats", "storyFormats", "targets").map { parent.resolve(it).toString() }
            }
            val scope = ReadAction.compute<StoryScope, RuntimeException> {
                requireNotNull(project.getService(StoryScopeService::class.java).snapshot(initial.story.id,
                    formatRoots + defaults + executable.parent.toString()))
            }
            val files = ReadAction.compute<List<com.intellij.openapi.vfs.VirtualFile>, RuntimeException> { scope.files() }
            ApplicationManager.getApplication().invokeAndWait({
                indicator.checkCanceled()
                val documents = FileDocumentManager.getInstance()
                for (file in files) documents.getCachedDocument(file)?.let { document ->
                    if (documents.isDocumentUnsaved(document)) documents.saveDocument(document)
                    check(!documents.isDocumentUnsaved(document)) { "Could not save selected input: ${file.path}" }
                }
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }, ModalityState.any())
            val context = ReadAction.compute<StoryContextService.Snapshot, RuntimeException> {
                project.getService(StoryContextService::class.java).compute(scope)
            }
            val explicit = !request.formatId.isNullOrBlank()
            require(context.format == StoryContextService.Format.HARLOWE_3 || (explicit && context.format in setOf(
                StoryContextService.Format.MISSING, StoryContextService.Format.UNSUPPORTED))) {
                "StoryData cannot select Harlowe 3 (${context.format}). Review StoryData or select an installed Harlowe 3 format."
            }
            val destination = directory.resolve(request.outputPath).normalize()
            val plan = TweegoBuildEngine.Plan(executable, directory, destination, files.map { Path.of(it.path) }, environment, request.formatId)
            val success = TweegoBuildEngine(TweegoProcessRunner(indicator, output)).build(plan,
                { indicator.checkCanceled(); if (project.isDisposed) throw ProcessCanceledException() }) { format ->
                output("\nEffective format: Harlowe ${format.version} (${format.id})\n", false)
            }
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(success.output)
            output("\nBuilt ${success.output}\n", false)
            Result(success.output, success.format.id, success.format.version)
        } catch (_: ProcessCanceledException) {
            output("\nBuild canceled; previous HTML preserved.\n", false)
            Result(canceled = true)
        } catch (exception: Exception) {
            val message = exception.message ?: exception.javaClass.simpleName
            output("\nBuild failed: $message\n", true)
            Result(error = message)
        }
    }
}
