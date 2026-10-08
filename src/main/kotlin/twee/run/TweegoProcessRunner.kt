package twee.run

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key

internal class TweegoProcessRunner(private val indicator: ProgressIndicator,
                                   private val output: (String, Boolean) -> Unit) : TweegoBuildEngine.Runner {
    override fun run(command: TweegoBuildEngine.Command): TweegoBuildEngine.ProcessResult {
        indicator.checkCanceled()
        val line = GeneralCommandLine(command.executable.toString())
            .withParameters(command.arguments).withWorkDirectory(command.directory.toFile())
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE)
            .withEnvironment(command.environment).withCharset(Charsets.UTF_8)
        val handler = CapturingProcessHandler(line)
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                output(event.text, outputType == ProcessOutputTypes.STDERR)
            }
        })
        try {
            val result = handler.runProcessWithProgressIndicator(indicator)
            if (result.isCancelled) throw ProcessCanceledException()
            indicator.checkCanceled()
            output("\nTweego exit code: ${result.exitCode}\n", false)
            return TweegoBuildEngine.ProcessResult(result.exitCode, result.stdout, result.stderr)
        } finally {
            if (!handler.isProcessTerminated) {
                handler.destroyProcess()
                handler.process.destroyForcibly()
                handler.waitFor()
            }
        }
    }
}
