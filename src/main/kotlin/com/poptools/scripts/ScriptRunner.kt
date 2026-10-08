package com.poptools.scripts

import com.intellij.execution.actions.StopProcessAction
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.*
import com.intellij.execution.ui.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.execution.ParametersListUtil
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import com.intellij.ui.dsl.builder.*

object ScriptRunner {
    private val startingKey = Key.create<MutableSet<String>>("poptool.starting.scripts")
    private val parameterDialogsKey = Key.create<MutableMap<String, ParameterDialog>>("poptool.parameter.dialogs")
    private fun parameterDialogs(project: Project): MutableMap<String, ParameterDialog> = project.getUserData(parameterDialogsKey)
        ?: hashMapOf<String, ParameterDialog>().also { project.putUserData(parameterDialogsKey, it) }
    private fun starting(project: Project): MutableSet<String> = project.getUserData(startingKey)
        ?: hashSetOf<String>().also { project.putUserData(startingKey, it) }
    private fun existing(project: Project, id: String): ScriptDescriptor? = RunContentManager.getInstance(project).allDescriptors
        .filterIsInstance<ScriptDescriptor>().find { it.scriptId == id }
    private fun alreadyRunning(project: Project, id: String): Boolean {
        val descriptor = existing(project, id)
        val active = descriptor?.processHandler?.let { !it.isProcessTerminated } == true
        if (active || id in starting(project)) {
            if (descriptor != null) RunContentManager.getInstance(project).toFrontRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
            return true
        }
        return false
    }

    @JvmStatic fun run(project: Project, source: ScriptDefinition, previous: Map<String, String>) {
        if (project.isDisposed) return
        parameterDialogs(project)[source.id]?.let { it.toFront(); return }
        if (alreadyRunning(project, source.id)) return
        val script = ScriptJson.copy(source)
        try {
            ScriptJson.validate(script)
            if (script.parameters.isNotEmpty()) {
                val dialogs = parameterDialogs(project)
                val dialog = ParameterDialog(project, script, previous, onExecute = { values -> execute(project, script, values) })
                dialogs[script.id] = dialog
                Disposer.register(dialog.disposable, Disposable { dialogs.remove(script.id, dialog) })
                dialog.show()
            } else execute(project, script, emptyMap())
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: e.toString(), "脚本无法运行")
        }
    }

    private fun execute(project: Project, script: ScriptDefinition, values: Map<String, String>) {
        if (project.isDisposed || alreadyRunning(project, script.id)) return
        try {
            // Re-read device selection and environment for every execution from the retained form.
            val serial = AndroidDevices.serialForScript(project, script)
            if (script.presentation.confirm_before_run && Messages.showYesNoDialog(project, "执行脚本“${script.title}”？", "确认执行", Messages.getQuestionIcon()) != Messages.YES) return
            val settings = ScriptLibrary.getInstance().paths()
            val base = project.basePath
            if (alreadyRunning(project, script.id)) return
            starting(project).add(script.id)
            ApplicationManager.getApplication().executeOnPooledThread { launch(project, script, values, settings, base, serial) }
        } catch (e: Exception) {
            starting(project).remove(script.id)
            Messages.showErrorDialog(project, e.message ?: e.toString(), "脚本无法运行")
        }
    }

    private fun launch(project: Project, script: ScriptDefinition, values: Map<String, String>, settings: Map<String, String>, base: String?, serial: String?) {
        var temporary: Path? = null
        try {
            if (project.isDisposed) return
            val dir = script.executor.cwd?.takeIf { it.isNotBlank() }?.let { ParameterTemplates.render(it, values) } ?: base
                ?: throw IllegalArgumentException("当前项目没有根目录，请为脚本指定绝对工作目录")
            var cwd = Path.of(dir)
            if (!cwd.isAbsolute) cwd = Path.of(base ?: throw IllegalArgumentException("相对工作目录需要项目根目录")).resolve(cwd)
            require(Files.isDirectory(cwd)) { "工作目录不存在：$cwd" }
            val resolver = EnvironmentResolver(settings, System.getenv())
            val type = script.executor.kind
            val interpreter = if (type == "batch") "cmd" else type
            val command = ParameterTemplates.render(script.executor.command, values)
            val args = ParameterTemplates.renderArguments(script.executor.args, values, script.parameters)
            val launchArgs = arrayListOf<String>()
            val executable = resolver.resolve(if (type == "process") command else interpreter, script.interpreter)
            val dependencies = script.executor.requirements.filter { it != "android_device" }.toMutableList()
            val environment = resolver.executionEnvironment(dependencies)
            if (serial != null) environment["ANDROID_SERIAL"] = serial
            script.executor.env.forEach { (k, v) -> environment[k] = ParameterTemplates.render(v, values) }
            environment["PYTHONUTF8"] = "1"; environment["PYTHONIOENCODING"] = "utf-8"; environment["PYTHONUNBUFFERED"] = "1"
            val cleanup = Files.createTempDirectory("poptool-script-")
            temporary = cleanup
            environment["POPTOOLS_OUTPUT_DIR"] = Files.createDirectories(cleanup.resolve("outputs")).toString()
            val parts = ParametersListUtil.parse(command)
            var file: Path? = null
            if (parts.isNotEmpty() && '\n' !in command) {
                try { file = cwd.resolve(parts.first()).takeIf { Files.isRegularFile(it) } } catch (_: InvalidPathException) { }
            }
            when (type) {
                "python" -> {
                    if (file != null) { launchArgs.add(file.toString()); launchArgs.addAll(parts.drop(1)) }
                    else {
                        val src = cleanup.resolve("script.py")
                        Files.writeString(src, command, StandardCharsets.UTF_8); launchArgs.add(src.toString())
                    }
                    launchArgs.addAll(args)
                }
                "powershell" -> {
                    launchArgs.addAll(listOf("-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command"))
                    val setup = "\$OutputEncoding = [System.Text.UTF8Encoding]::new(\$false); [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
                    launchArgs.add(setup + command); launchArgs.addAll(args)
                }
                "bash" -> { launchArgs.addAll(listOf("-c", command, "poptool-script")); launchArgs.addAll(args) }
                "batch" -> {
                    if (file == null) {
                        file = cleanup.resolve("script.cmd")
                        Files.writeString(file, "@chcp 65001 >nul\r\n" + command.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n"), StandardCharsets.UTF_8)
                    }
                    launchArgs.addAll(listOf("/d", "/q", "/c", file.toString())); launchArgs.addAll(args)
                }
                else -> launchArgs.addAll(args)
            }
            val line = GeneralCommandLine(executable.toString()).withParameters(launchArgs).withWorkDirectory(cwd.toFile()).withCharset(Charset.forName(script.executor.encoding))
            line.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE).withEnvironment(environment)
            if (project.isDisposed) { deleteTemporarySources(cleanup); return }
            val handler = ScriptProcessHandler(line)
            handler.processInput?.close()
            handler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(e: ProcessEvent) = deleteTemporarySources(cleanup)
            })
            val lifetime = Disposer.newDisposable("PopTool ${script.title}")
            Disposer.register(lifetime, Disposable { if (!handler.isProcessTerminated) handler.destroyProcess() })
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) { handler.destroyProcess(); handler.startNotify(); return@invokeLater }
                Disposer.register(project, lifetime)
                val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
                console.attachToProcess(handler)
                console.print("运行：${script.title}\n" + (if (serial == null) "" else "默认 Android 设备：$serial（脚本显式设备参数优先）\n"), ConsoleViewContentType.SYSTEM_OUTPUT)
                console.print("脚本输出目录：${cleanup.resolve("outputs")}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                val again = object : DumbAwareAction("再次运行") {
                    override fun actionPerformed(e: AnActionEvent) {
                        val latest = ScriptLibrary.getInstance().list(false).find { it.id == script.id } ?: script
                        run(project, latest, values)
                    }
                }
                val stop = StopProcessAction("停止", "停止本次脚本及子进程", handler)
                val content = consolePanel(console, again, stop)
                val descriptor = ScriptDescriptor(console, handler, content, script.title, script.id)
                Disposer.register(lifetime, console)
                Disposer.register(descriptor, Disposable { Disposer.dispose(lifetime) })
                handler.addProcessListener(object : ProcessAdapter() {
                    override fun processTerminated(e: ProcessEvent) {
                        console.print("\n" + (if (handler.stopped) "运行已停止" else "退出码：${e.exitCode}") + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        deleteTemporarySources(cleanup)
                    }
                })
                val manager = RunContentManager.getInstance(project)
                val previous = existing(project, script.id)
                if (previous != null) {
                    previous.reusing = true; descriptor.reusing = true
                    try { manager.showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor, previous) }
                    finally { previous.reusing = false; descriptor.reusing = false }
                } else manager.showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
                handler.startNotify()
                ScriptLibrary.getInstance().recordUsage(script.id)
                script.executor.timeout_seconds?.let { seconds ->
                    val timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule({
                        if (!handler.isProcessTerminated) {
                            console.print("\n执行超时，正在停止。\n", ConsoleViewContentType.ERROR_OUTPUT); handler.destroyProcess()
                        }
                    }, seconds.toLong(), TimeUnit.SECONDS)
                    handler.addProcessListener(object : ProcessAdapter() { override fun processTerminated(e: ProcessEvent) { timeout.cancel(false) } })
                    if (handler.isProcessTerminated) timeout.cancel(false)
                }
            }
        } catch (e: Exception) {
            temporary?.let(::deleteTemporarySources)
            ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) Messages.showErrorDialog(project, e.message ?: e.toString(), "脚本无法运行") }
        } finally {
            ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) starting(project).remove(script.id) }
        }
    }

    private class ScriptDescriptor(console: ConsoleView, handler: ProcessHandler, content: JComponent, title: String, val scriptId: String) : RunContentDescriptor(console, handler, content, title) {
        var reusing = false
        init {
            setReusePolicy(object : RunContentDescriptorReusePolicy() {
                override fun canBeReusedBy(other: RunContentDescriptor) = other is ScriptDescriptor && scriptId == other.scriptId
            })
        }
        override fun isContentReuseProhibited() = !reusing
    }
    private fun deleteTemporarySources(dir: Path) {
        // Keep generated outputs; remove only source files created by this launcher.
        for (name in listOf("script.py", "script.cmd")) try { Files.deleteIfExists(dir.resolve(name)) } catch (_: Exception) { }
        try { Files.deleteIfExists(dir.resolve("outputs")) } catch (_: Exception) { }
        try { Files.deleteIfExists(dir) } catch (_: Exception) { }
    }
    class ScriptProcessHandler(line: GeneralCommandLine) : KillableColoredProcessHandler(line) {
        @Volatile var stopped = false
        init { setShouldDestroyProcessRecursively(true) }
        override fun destroyProcessImpl() { stopped = true; super.destroyProcessImpl() }
    }
    private fun consolePanel(console: ConsoleView, vararg actions: AnAction): JComponent {
        val toolbar = ActionManager.getInstance().createActionToolbar("PopToolRun", DefaultActionGroup(*actions), true)
        return panel {
            row { cell(toolbar.component).align(AlignX.FILL).resizableColumn() }
            row { cell(console.component).align(Align.FILL).resizableColumn() }.resizableRow()
        }.also { toolbar.targetComponent = it }
    }
}
