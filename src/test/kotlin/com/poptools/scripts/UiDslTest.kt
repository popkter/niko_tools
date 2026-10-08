package com.poptools.scripts

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import java.awt.Component
import java.awt.Container
import java.nio.file.Files

/** Exercise native DSL bindings and dialog construction in a real test application. */
class UiDslTest : LightPlatformTestCase() {
    fun testExecutionDialogKeepsParametersForRepeatedRuns() {
        val script = ScriptDefinition().also { it.title = "Repeat"; it.executor.command = "echo hello" }
        script.parameters = ParameterTemplates.synchronize(listOf("${'$'}{name:hello}"), emptyList())
        val runs = arrayListOf<Map<String, String>>()
        val dialog = ParameterDialog(project, script, emptyMap(), onExecute = { runs.add(it) })
        try {
            val builder = dialog.javaClass.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }
            val field = descendants(builder.invoke(dialog) as Component).filterIsInstance<javax.swing.JTextField>().single()
            dialog.performOKAction()
            assertEquals(listOf(mapOf("name" to "hello")), runs)
            assertFalse(dialog.isOK)
            assertFalse(Disposer.isDisposed(dialog.disposable))
            field.text = "second"
            dialog.performOKAction()
            assertEquals(listOf(mapOf("name" to "hello"), mapOf("name" to "second")), runs)
            assertFalse(Disposer.isDisposed(dialog.disposable))
            field.text = ""
            dialog.performOKAction()
            assertEquals(2, runs.size)
            assertFalse(Disposer.isDisposed(dialog.disposable))
        } finally { Disposer.dispose(dialog.disposable) }
    }

    fun testPreviewDialogStillAcceptsAndCloses() {
        val script = ScriptDefinition().also { it.executor.command = "echo hello" }
        script.parameters = ParameterTemplates.synchronize(listOf("${'$'}{name:hello}"), emptyList())
        val dialog = ParameterDialog(project, script, emptyMap(), "预览")
        try {
            // Headless DialogWrapper peers do not report native window modality.
            dialog.performOKAction()
            assertTrue(dialog.isOK)
            assertEquals(mapOf("name" to "hello"), dialog.values())
        } finally { Disposer.dispose(dialog.disposable) }
    }

    fun testTemplateListSwitchesDescriptionAndCode() {
        val dialog = TemplateHelpDialog(project)
        try {
            val builder = dialog.javaClass.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }
            val splitter = builder.invoke(dialog) as com.intellij.ui.OnePixelSplitter
            val list = descendants(splitter.firstComponent).filterIsInstance<javax.swing.JList<*>>().single()
            val fields = descendants(splitter.secondComponent).filterIsInstance<com.intellij.ui.components.JBTextArea>().toList()
            assertEquals(3, fields.size)
            val description = fields.single { it.name == "template.description" }
            val code = fields.single { it.name == "template.code" }
            val preview = fields.single { it.name == "template.preview" }
            val cards = com.google.gson.JsonParser.parseReader(
                requireNotNull(javaClass.getResourceAsStream("/poptool-guide.json")).reader(Charsets.UTF_8)
            ).asJsonArray.flatMap { section ->
                val data = section.asJsonObject
                if (data["id"].asString in setOf("syntax", "templates"))
                    data.getAsJsonArray("cards").filter { it.asJsonObject.has("code") }
                else emptyList()
            }
            assertEquals(cards.size, list.model.size)
            assertEquals(0, list.selectedIndex)
            assertFalse(description.isEditable)
            assertFalse(code.isEditable)
            // A new selection refreshes the live result from its defaults and keeps the original snippet.
            for (index in listOf(0, cards.lastIndex, 1, 0)) {
                if (index != list.selectedIndex) preview.text = "preview result"
                list.selectedIndex = index
                assertEquals(cards[index].asJsonObject["title"].asString, list.selectedValue.toString())
                assertEquals(cards[index].asJsonObject["body"].asString, description.text)
                assertEquals(cards[index].asJsonObject["code"].asString, code.text)
                assertEquals(0, code.caretPosition)
                val parameters = ParameterTemplates.synchronize(listOf(code.text), emptyList())
                val defaults = parameters.associate { it.id to (it.defaultValue?.toString() ?: "") }
                val expected = if (parameters.any { it.required && defaults.getValue(it.id).isBlank() }) ""
                    else ParameterTemplates.render(code.text, defaults)
                assertEquals(expected, preview.text)
            }
        } finally { Disposer.dispose(dialog.disposable) }
    }

    fun testTemplatePreviewUpdatesLiveAndLeavesSourceIntact() {
        val dialog = TemplateHelpDialog(project)
        try {
            val builder = dialog.javaClass.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }
            val splitter = builder.invoke(dialog) as com.intellij.ui.OnePixelSplitter
            val list = descendants(splitter.firstComponent).filterIsInstance<javax.swing.JList<*>>().single()
            val detail = splitter.secondComponent
            fun area(name: String) = descendants(detail).filterIsInstance<com.intellij.ui.components.JBTextArea>().single { it.name == name }
            val code = area("template.code")
            val preview = area("template.preview")
            assertFalse(descendants(detail).filterIsInstance<javax.swing.JButton>().any { it.text == "预览参数替换" })
            val source = code.text
            assertEquals("", preview.text)
            assertTrue(descendants(detail).filterIsInstance<javax.swing.JLabel>().any { it.text.contains("请填写") })
            val input = descendants(detail).filterIsInstance<javax.swing.JTextField>().single()
            input.text = "hello preview"
            assertEquals("hello preview", preview.text)
            assertEquals(source, code.text)
            input.text = "updated"
            assertEquals("updated", preview.text)
            assertEquals(source, code.text)
            list.selectedIndex = 1
            assertEquals("默认值", preview.text)
            list.selectedIndex = 2
            assertEquals("1", preview.text)
            val choice = descendants(detail).filterIsInstance<javax.swing.JComboBox<*>>().single()
            choice.selectedIndex = 1
            assertEquals("0", preview.text)
            list.selectedIndex = 3
            val file = descendants(detail).filterIsInstance<TextFieldWithBrowseButton>().single()
            file.text = "/tmp/example.apk"
            assertEquals("/tmp/example.apk", preview.text)
            list.selectedIndex = 5
            assertEquals("adb logcat \"*:I\"", preview.text)
        } finally { Disposer.dispose(dialog.disposable) }
    }

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

    fun testDefaultEnvironmentPathsAreVisibleWithoutSavingOverrides() {
        val library = ScriptLibrary.getInstance()
        val oldState = library.state
        val directory = Files.createTempDirectory("niko-default-path")
        val python = Files.writeString(directory.resolve("python.exe"), "")
        val settings = EnvironmentSettings(mapOf("PATH" to directory.toString()))
        try {
            library.setPaths(emptyMap())
            val panel = settings.createPanel()
            val fields = descendants(panel).filterIsInstance<TextFieldWithBrowseButton>().toList()
            assertEquals("", fields[0].text)
            assertEquals(python.toString(), (fields[0].textField as com.intellij.ui.components.JBTextField).emptyText.text)
            assertTrue(descendants(panel).filterIsInstance<javax.swing.text.JTextComponent>().any { it.text.contains(python.toString()) })
            assertFalse(panel.isModified())
            assertTrue(panel.validateAll().isEmpty())
            panel.apply()
            assertTrue(library.paths().isEmpty())
            fields[0].text = python.toString()
            panel.apply()
            assertEquals(python.toString(), library.paths()["python"])
            fields[0].text = ""
            panel.apply()
            assertFalse(library.paths().containsKey("python"))
            assertEquals(python.toString(), (fields[0].textField as com.intellij.ui.components.JBTextField).emptyText.text)
        } finally {
            settings.disposeUIResources()
            library.loadState(oldState)
            Files.delete(python); Files.delete(directory)
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

    fun testFileChoosersPreferExistingInputAndFallBackToOwningProjectRoot() {
        val directory = Files.createTempDirectory("niko-project-chooser")
        val a = Files.createDirectory(directory.resolve("A"))
        val b = Files.createDirectory(directory.resolve("B"))
        val file = Files.writeString(b.resolve("input.txt"), "input")
        val fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        val rootA = requireNotNull(fs.refreshAndFindFileByNioFile(a))
        val rootB = requireNotNull(fs.refreshAndFindFileByNioFile(b))
        val input = requireNotNull(fs.refreshAndFindFileByNioFile(file))
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
                field.text = b.toString()
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                field.text = file.toString()
                assertEquals(input, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                field.text = "input.txt"
                assertEquals(input, ProjectRootBrowseListener(descriptor, projectB, field).getInitialFile())
                for (invalid in listOf("", "   ", b.resolve("missing.txt").toString(), "bad\u0000path")) {
                    field.text = invalid
                    assertEquals(rootA, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                    assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectB, field).getInitialFile())
                }
            }
            // The platform test fixture supplies the project context for application-level settings.
            val listener = ProjectRootBrowseListener(com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor(), null, field)
            assertEquals(projectRoot(project), listener.getInitialFile())
        } finally {
            field.dispose()
            Files.delete(file); Files.delete(b); Files.delete(a); Files.delete(directory)
        }
    }

    fun testDeleteKeyRequiresSelectionAndConfirmation() {
        val library = ScriptLibrary.getInstance()
        val oldState = library.state
        val previousDialog = com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.DEFAULT)
        try {
            library.loadState(ScriptLibrary.State())
            val script = ScriptDefinition().also { it.title = "Delete me"; it.executor.command = "echo hello" }
            library.save(script, false)
            val table = ScriptToolWindow.ScriptPanel(project).scriptTable
            val deleteKey = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0)
            val action = table.actionMap.get(table.inputMap.get(deleteKey))
            assertNotNull(action)
            assertEquals(table.inputMap.get(deleteKey), table.inputMap.get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, 0)))
            var prompts = 0
            var answer = com.intellij.openapi.ui.Messages.NO
            com.intellij.openapi.ui.TestDialogManager.setTestDialog { message ->
                prompts++
                assertTrue(message.contains(script.title))
                answer
            }
            val event = java.awt.event.ActionEvent(table, 0, "delete")
            action.actionPerformed(event)
            assertEquals(0, prompts)
            table.setRowSelectionInterval(0, 0)
            action.actionPerformed(event)
            assertEquals(1, prompts)
            assertEquals(1, library.list(false).size)
            answer = com.intellij.openapi.ui.Messages.YES
            action.actionPerformed(event)
            assertEquals(2, prompts)
            assertTrue(library.list(false).isEmpty())
        } finally {
            com.intellij.openapi.ui.TestDialogManager.setTestDialog(previousDialog)
            library.loadState(oldState)
        }
    }

    fun testSortingPreservesSelection() {
        val library = ScriptLibrary.getInstance()
        val oldState = library.state
        try {
            library.loadState(ScriptLibrary.State())
            val z = ScriptDefinition().also { it.title = "Zulu"; it.executor.command = "echo z" }
            val a = ScriptDefinition().also { it.title = "Alpha"; it.executor.command = "echo a" }
            library.save(z, false); library.save(a, false)
            val table = ScriptToolWindow.ScriptPanel(project).scriptTable
            table.setRowSelectionInterval(0, 0)
            library.setSortOrder(ScriptLibrary.SortOrder.NAME)
            assertEquals("Alpha", table.getValueAt(0, 0).toString())
            assertEquals(z.id, (table.getValueAt(table.selectedRow, 0) as ScriptDefinition).id)
            library.setSortOrder(ScriptLibrary.SortOrder.USAGE)
            library.recordUsage(a.id)
            assertEquals("Alpha", table.getValueAt(0, 0).toString())
            assertEquals(z.id, (table.getValueAt(table.selectedRow, 0) as ScriptDefinition).id)
        } finally { library.loadState(oldState) }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) for (child in component.components) yieldAll(descendants(child))
    }
}
