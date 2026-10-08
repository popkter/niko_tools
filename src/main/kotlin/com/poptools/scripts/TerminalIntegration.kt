package com.poptools.scripts

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.jediterm.terminal.ui.*
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Consumer
import javax.swing.KeyStroke

private val modernMenuUsers = hashMapOf<DefaultActionGroup, Int>()

/** Classic terminal builds its own action chain rather than an XML menu group. */
class TerminalIntegration : StartupActivity.DumbAware {
    private fun installModernMenu(project: Project) {
        val actions = ActionManager.getInstance()
        val action = actions.getAction("PopTool.NewFromTerminal") ?: return
        val group = actions.getAction("Terminal.ReworkedTerminalContextMenu") as? DefaultActionGroup ?: return
        synchronized(modernMenuUsers) {
            val users = modernMenuUsers.getOrDefault(group, 0)
            if (users == 0) group.add(action)
            modernMenuUsers[group] = users + 1
        }
        Disposer.register(project, Disposable {
            synchronized(modernMenuUsers) {
                val users = modernMenuUsers.getOrDefault(group, 1) - 1
                if (users == 0) { modernMenuUsers.remove(group); group.remove(action) }
                else modernMenuUsers[group] = users
            }
        })
    }

    override fun runActivity(project: Project) {
        installModernMenu(project)
        val manager = TerminalToolWindowManager.getInstance(project)
        val registered = Collections.newSetFromMap(WeakHashMap<TerminalWidget, Boolean>())
        val install = Consumer<TerminalWidget> { widget ->
            if (!registered.add(widget)) return@Consumer
            val classic = JBTerminalWidget.asJediTermWidget(widget) ?: return@Consumer
            val old = classic.nextProvider
            classic.nextProvider = object : TerminalActionProvider {
                private var next = old
                override fun getActions(): List<TerminalAction> = listOf(
                    TerminalAction(TerminalActionPresentation("新建 NikoTools 自定义脚本", emptyList<KeyStroke>())) {
                        classic.selectedText?.takeIf { it.isNotEmpty() }?.let { NewFromTerminalAction.open(project, it) }
                        true
                    }.withEnabledSupplier { !classic.selectedText.isNullOrEmpty() }.separatorBefore(true)
                )
                override fun getNextProvider(): TerminalActionProvider? = next
                override fun setNextProvider(provider: TerminalActionProvider?) { next = provider }
            }
            Disposer.register(project, Disposable { classic.nextProvider = old })
        }
        manager.addNewTerminalSetupHandler(install, project)
        manager.terminalWidgets.forEach(install::accept)
    }
}
