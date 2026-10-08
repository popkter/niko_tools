package com.poptools.scripts

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidget

class NewFromTerminalAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val text = selection(e)?.takeIf { it.isNotEmpty() } ?: return
        open(project, text)
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null && !selection(e).isNullOrEmpty() }
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    companion object {
        @JvmStatic fun selection(e: AnActionEvent): String? {
            val modern = ModernTerminalSelection.read(e.dataContext)
            if (modern.available) return modern.text
            return e.getData(JBTerminalWidget.SELECTED_TEXT_DATA_KEY) ?: e.getData(CommonDataKeys.EDITOR)?.selectionModel?.selectedText
        }
        @JvmStatic fun open(project: Project, text: String) {
            val script = ScriptDefinition().also { it.executor.command = text }
            ScriptEditor(project, script, false).show()
        }
    }
}
