package twee.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** One handler owns discovery, compile, promotion and launch; the standard Run Stop action cancels it. */
internal class TweegoRunProcess(private val project: Project, private val request: TweegoBuildService.Request,
                                private val launch: Boolean, private val browserId: String?) : ProcessHandler(), Disposable {
    private val indicator = EmptyProgressIndicator()
    private val started = AtomicBoolean()
    init { Disposer.register(project, this) }

    override fun startNotify() {
        if (!started.compareAndSet(false, true)) return
        super.startNotify() // The execution console is attached before any worker output.
        AppExecutorUtil.getAppExecutorService().execute {
            val result = try {
                project.getService(TweegoBuildService::class.java).build(request, indicator) { text, error ->
                    notifyTextAvailable(text, if (error) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT)
                }
            } catch (_: ProcessCanceledException) { TweegoBuildService.Result(canceled = true) }
            catch (error: Exception) {
                notifyTextAvailable("Build failed: ${error.message}\n", ProcessOutputTypes.STDERR)
                TweegoBuildService.Result(error = error.message ?: "Build failed")
            }
            ApplicationManager.getApplication().invokeLater {
                var exit = if (result.successful) 0 else if (result.canceled) 130 else 1
                try {
                    if (indicator.isCanceled || project.isDisposed) exit = 130
                    else TweegoLaunchPolicy.open(result, launch, indicator.isCanceled) { path ->
                        val manager = WebBrowserManager.getInstance()
                        val browser = browserId?.takeIf { it.isNotBlank() }?.let { id ->
                            requireNotNull(manager.activeBrowsers.firstOrNull { it.id.toString() == id }) {
                                "Configured browser is unavailable. Select an active browser in the run configuration."
                            }
                        }
                        BrowserLauncher.instance.browse(path.toUri().toASCIIString(), browser, project)
                    }
                } catch (error: Exception) {
                    exit = 1
                    notifyTextAvailable("Browser launch failed: ${error.message}\n", ProcessOutputTypes.STDERR)
                } finally {
                    notifyTextAvailable("\nTwee task finished with exit code $exit\n", ProcessOutputTypes.SYSTEM)
                    notifyProcessTerminated(exit)
                    Disposer.dispose(this)
                }
            }
        }
    }
    override fun destroyProcessImpl() { indicator.cancel() }
    override fun detachProcessImpl() { indicator.cancel() } // No detached background builds.
    override fun detachIsDefault() = false
    override fun getProcessInput(): OutputStream? = null
    override fun dispose() { indicator.cancel() }
}

internal object TweegoLaunchPolicy {
    /** Never infer success from an existing output path: the service must return a promoted result. */
    fun open(result: TweegoBuildService.Result, requested: Boolean, canceled: Boolean, open: (Path) -> Unit) {
        if (requested && !canceled && result.successful) {
            val path = requireNotNull(result.output)
            check(Files.isRegularFile(path) && Files.size(path) > 0) { "Built HTML is no longer available." }
            open(path)
        }
    }
}
