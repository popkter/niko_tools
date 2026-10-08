package com.poptools.scripts

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

internal object ScriptIcons {
    private val process = IconLoader.getIcon("/icons/process.svg", ScriptIcons::class.java)

    fun forKind(kind: String): Icon = when (kind) {
        "python" -> AllIcons.Language.Python
        "process" -> process
        else -> AllIcons.Nodes.Console
    }
}
