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

internal fun projectRoot(project: Project?): VirtualFile? = project?.basePath?.let {
    LocalFileSystem.getInstance().findFileByPath(it)?.takeIf { file -> file.isDirectory }
}

/** Start in the owning window's project, without restricting browsing outside that project. */
internal class ProjectRootBrowseListener(
    descriptor: FileChooserDescriptor,
    private val owningProject: Project?,
    private val owner: TextFieldWithBrowseButton
) : TextBrowseFolderListener(descriptor, owningProject
    ?: CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(owner))) {
    // BrowseFolderRunnable.getProject() is final on platform 253.
    private fun resolveOwningProject(): Project? = owningProject
        ?: CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(owner))

    public override fun getInitialFile(): VirtualFile? = projectRoot(resolveOwningProject()) ?: super.getInitialFile()
}

internal fun Row.projectPathField(
    project: Project? = null,
    descriptor: FileChooserDescriptor
): Cell<TextFieldWithBrowseButton> = textFieldWithBrowseButton(project = project, fileChooserDescriptor = descriptor)
    .applyToComponent {
        button.actionListeners.forEach(button::removeActionListener)
        addBrowseFolderListener(ProjectRootBrowseListener(descriptor, project, this))
    }
