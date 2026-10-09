package com.poptools.scripts

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import java.nio.file.Files
import java.nio.file.Path

internal fun projectRoot(project: Project?): VirtualFile? = project?.basePath?.let {
    LocalFileSystem.getInstance().findFileByPath(it)?.takeIf { file -> file.isDirectory }
}

/** Open the input directory (or a file's parent), falling back to the owning project root. */
internal class ProjectRootBrowseListener(
    descriptor: FileChooserDescriptor,
    private val owningProject: Project?,
    private val owner: TextFieldWithBrowseButton
) : TextBrowseFolderListener(FileChooserDescriptor(descriptor).apply {
    // Both native and IDE choosers must honor the input rather than a remembered location.
    putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, false)
}, owningProject
    ?: CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(owner))) {
    // BrowseFolderRunnable.getProject() is final on platform 253.
    private fun resolveOwningProject(): Project? = owningProject
        ?: CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(owner))

    public override fun getInitialFile(): VirtualFile? {
        val project = resolveOwningProject()
        val current = owner.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
            try {
                // Paths copied from a shell may include surrounding quotes, especially on Windows.
                val unquoted = if (text.length >= 2 &&
                    ((text.first() == '"' && text.last() == '"') || (text.first() == '\'' && text.last() == '\'')))
                    text.substring(1, text.length - 1) else text
                if (unquoted.isBlank()) return@let null
                var path = Path.of(unquoted)
                if (!path.isAbsolute) {
                    val base = project?.basePath ?: return@let null
                    path = Path.of(base).resolve(path)
                }
                path = path.normalize()
                // Generated files can exist on disk before the IDE has indexed them in the VFS.
                // Resolve the directory directly instead of requiring a VirtualFile for the leaf.
                val directory = when {
                    Files.isDirectory(path) -> path
                    Files.exists(path) -> path.parent
                    else -> null
                } ?: return@let null
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)
                    ?.takeIf { it.isValid && it.isDirectory }
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
