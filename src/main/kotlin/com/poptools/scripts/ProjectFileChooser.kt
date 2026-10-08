package com.poptools.scripts

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import java.nio.file.Path

internal fun projectRoot(project: Project?): VirtualFile? = project?.basePath?.let {
    LocalFileSystem.getInstance().findFileByPath(it)?.takeIf { file -> file.isDirectory }
}

/** Prefer an existing input path, falling back to the owning window's project root. */
internal class ProjectRootBrowseListener(
    descriptor: FileChooserDescriptor,
    private val owningProject: Project?,
    private val owner: TextFieldWithBrowseButton
) : TextBrowseFolderListener(descriptor, owningProject
    ?: CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(owner))) {
    // BrowseFolderRunnable.getProject() is final on platform 253.
    private fun resolveOwningProject(): Project? = owningProject
        ?: CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(owner))

    public override fun getInitialFile(): VirtualFile? {
        val project = resolveOwningProject()
        val current = owner.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
            try {
                var path = Path.of(text)
                if (!path.isAbsolute) {
                    val base = project?.basePath ?: return@let null
                    path = Path.of(base).resolve(path)
                }
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path.normalize())?.takeIf { it.isValid }
            } catch (_: java.nio.file.InvalidPathException) { null }
        }
        return current ?: projectRoot(project)
    }
}

internal fun Row.projectPathField(
    project: Project? = null,
    descriptor: FileChooserDescriptor
): Cell<TextFieldWithBrowseButton> = textFieldWithBrowseButton(project = project, fileChooserDescriptor = descriptor)
    .applyToComponent {
        button.actionListeners.forEach(button::removeActionListener)
        addBrowseFolderListener(ProjectRootBrowseListener(descriptor, project, this))
    }
