package com.poptools.scripts

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

class ManageScriptsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow("NikoTools")?.show()
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
