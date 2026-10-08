package com.poptools.scripts

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import java.awt.Component
import java.awt.Container
import java.nio.file.Files

/** Exercise native DSL bindings and dialog construction in a real test application. */
class UiDslTest : LightPlatformTestCase() {
    fun testEnvironmentBindingsApplyAndReset() {
        val library = ScriptLibrary.getInstance()
        val oldState = library.state
        val file = Files.createTempFile("niko-settings-test", ".exe")
        val settings = EnvironmentSettings()
        try {
            library.setPaths(emptyMap())
            val panel = settings.createPanel()
            val fields = descendants(panel).filterIsInstance<TextFieldWithBrowseButton>().toList()
            assertEquals(6, fields.size)
            assertFalse(panel.isModified())
            fields[0].text = file.toString()
            assertTrue(panel.isModified())
            assertTrue(panel.validateAll().isEmpty())
            panel.apply()
            assertEquals(file.toString(), library.paths()["python"])
            assertFalse(panel.isModified())
            fields[0].text = "discard me"
            panel.reset()
            assertEquals(file.toString(), fields[0].text)
            library.setPaths(mapOf("python" to "external change"))
            panel.reset()
            assertEquals("external change", fields[0].text)
        } finally {
            settings.disposeUIResources()
            library.loadState(oldState)
            Files.deleteIfExists(file)
        }
    }

    fun testDslDialogsAndToolWindowConstruct() {
        val script = ScriptDefinition().also { it.executor.command = "echo hello" }
        // Exercise path fields in both the editor and the run-parameter dialog on each supported SDK.
        script.parameters = ParameterTemplates.synchronize(listOf("\${name:hello}", "\${input@file}", "\${output@dir}"), emptyList())
        val dialogs = listOf(ScriptEditor(project, script, false), ParameterDialog(project, script, emptyMap()), TemplateHelpDialog(project))
        try {
            // Headless DialogWrapper peers have no window content pane; test the DSL builders directly.
            dialogs.forEach { dialog ->
                val builder = dialog.javaClass.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }
                assertTrue(descendants(builder.invoke(dialog) as Component).any { it is com.intellij.openapi.ui.DialogPanel })
            }
            val panel = ScriptToolWindow.ScriptPanel(project)
            assertEquals(3, panel.scriptTable.columnCount)
            assertSame(panel, panel.component.getClientProperty("poptool.scriptPanel"))
        } finally { dialogs.forEach { Disposer.dispose(it.disposable) } }
    }
    fun testSelectedRowBackgroundIncludesIconAndAllColumns() {
        val library = ScriptLibrary.getInstance()
        val oldState = library.state
        try {
            library.loadState(ScriptLibrary.State())
            library.save(ScriptDefinition().also { it.title = "Selected script"; it.executor.command = "echo hello" }, false)
            val table = ScriptToolWindow.ScriptPanel(project).scriptTable
            table.background = java.awt.Color(24, 24, 24)
            table.selectionBackground = java.awt.Color(45, 75, 130)
            table.setSize(640, table.rowHeight * 2)
            table.doLayout()
            table.setRowSelectionInterval(0, 0)
            val image = java.awt.image.BufferedImage(table.width, table.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            try { table.paint(graphics) } finally { graphics.dispose() }
            // Above the glyphs: check the left inset, icon background and every column seam.
            for (x in 1 until table.width - 1) {
                assertEquals("Selected background at x=$x", table.selectionBackground.rgb, image.getRGB(x, 2))
            }
            table.clearSelection()
            val unselected = image.createGraphics()
            try { table.paint(unselected) } finally { unselected.dispose() }
            assertEquals("Clearing selection restores icon background", table.background.rgb, image.getRGB(8, 2))
            val preview = java.nio.file.Path.of("build/reports/ui/script-row-selection.png")
            Files.createDirectories(preview.parent)
            table.setRowSelectionInterval(0, 0)
            val selected = image.createGraphics()
            try { table.paint(selected) } finally { selected.dispose() }
            javax.imageio.ImageIO.write(image, "png", preview.toFile())
        } finally { library.loadState(oldState) }
    }

    fun testFileChoosersStartAtOwningProjectRoot() {
        val directory = Files.createTempDirectory("niko-project-chooser")
        val a = Files.createDirectory(directory.resolve("A"))
        val b = Files.createDirectory(directory.resolve("B"))
        val fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        val rootA = requireNotNull(fs.refreshAndFindFileByNioFile(a))
        val rootB = requireNotNull(fs.refreshAndFindFileByNioFile(b))
        fun projectAt(path: java.nio.file.Path): com.intellij.openapi.project.Project = java.lang.reflect.Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(com.intellij.openapi.project.Project::class.java)
        ) { _, method, _ ->
            when (method.name) { "getBasePath" -> path.toString(); "isDisposed" -> false; "toString" -> path.toString(); else -> null }
        } as com.intellij.openapi.project.Project
        val projectA = projectAt(a)
        val projectB = projectAt(b)
        val field = TextFieldWithBrowseButton().apply { text = b.toString() }
        try {
            for (descriptor in listOf(
                com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor(),
                com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )) {
                assertEquals(rootA, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectB, field).getInitialFile())
            }
            // The platform test fixture supplies the project context for application-level settings.
            val listener = ProjectRootBrowseListener(com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor(), null, field)
            assertEquals(projectRoot(project), listener.getInitialFile())
        } finally {
            field.dispose()
            Files.delete(b); Files.delete(a); Files.delete(directory)
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) for (child in component.components) yieldAll(descendants(child))
    }
}
