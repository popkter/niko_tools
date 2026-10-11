package com.poptools.scripts

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import java.awt.Component
import java.awt.Container
import java.nio.file.Files

/** Exercise native DSL bindings and dialog construction in a real test application. */
class UiDslTest : LightPlatformTestCase() {
    fun testScriptProcessHandlerHidesStartupCommandAndPreservesOutput() {
        val windows = com.intellij.openapi.util.SystemInfo.isWindows
        val line = if (windows) com.intellij.execution.configurations.GeneralCommandLine("cmd.exe").withParameters(
            "/d", "/q", "/c", "echo visible-output & echo visible-error 1>&2 & rem private-script-marker"
        ) else com.intellij.execution.configurations.GeneralCommandLine("/bin/sh").withParameters(
            "-c", "printf 'visible-output\\n'; printf 'visible-error\\n' >&2; # private-script-marker"
        )
        val handler = ScriptRunner.ScriptProcessHandler(line)
        val output = java.util.Collections.synchronizedList(arrayListOf<Pair<com.intellij.openapi.util.Key<*>, String>>())
        handler.addProcessListener(object : com.intellij.execution.process.ProcessAdapter() {
            override fun onTextAvailable(event: com.intellij.execution.process.ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                output.add(outputType to event.text)
            }
        })
        try {
            handler.notifyTextAvailable("运行提示\n", com.intellij.execution.process.ProcessOutputTypes.SYSTEM)
            handler.startNotify()
            assertTrue(com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread<Boolean> {
                handler.waitFor(10_000)
            }.get(15, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(0, handler.exitCode)
            val events = synchronized(output) { output.toList() }
            assertTrue(events.filter { it.first == com.intellij.execution.process.ProcessOutputTypes.STDOUT }.joinToString("") { it.second }.contains("visible-output"))
            assertTrue(events.filter { it.first == com.intellij.execution.process.ProcessOutputTypes.STDERR }.joinToString("") { it.second }.contains("visible-error"))
            assertTrue(events.any { it.first == com.intellij.execution.process.ProcessOutputTypes.SYSTEM && it.second == "运行提示\n" })
            assertFalse(events.any { it.second.contains("private-script-marker") })
        } finally {
            if (!handler.isProcessTerminated) handler.destroyProcess()
        }
    }

    fun testExplicitControlsValidateNumbersAndSubmitRadioValues() {
        val script = ScriptDefinition().also {
            it.executor.command = "echo hello"
            it.parameters = ParameterTemplates.synchronize(listOf(
                "\${次数@integer:3} \${比例@number:0.5} \${启用@boolean:true} " +
                    "\${环境@radio:开发=dev|生产=prod} \${说明@multiline:first} \${令牌@secret}"
            ), emptyList())
        }
        val library = ScriptLibrary.getInstance()
        val oldState = library.state
        library.save(script, false)
        val submitted = arrayListOf<Map<String, String>>()
        val dialog = ParameterDialog(project, script, mapOf("环境" to "prod"), onExecute = { submitted.add(it); false })
        try {
            val builder = dialog.javaClass.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }
            val content = builder.invoke(dialog) as Component
            val components = descendants(content).toList()
            assertFalse(components.filterIsInstance<javax.swing.JLabel>().any { it.text.endsWith(" *") })
            val radio = components.filterIsInstance<javax.swing.JRadioButton>()
            assertEquals(listOf("开发", "生产"), radio.map { it.text })
            assertEquals(javax.swing.BoxLayout.X_AXIS, (radio.first().parent.layout as javax.swing.BoxLayout).axis)
            assertTrue(radio[1].isSelected)
            radio[0].doClick()
            assertFalse(radio[1].isSelected)
            val flag = components.filterIsInstance<javax.swing.JCheckBox>().single { it.text == "" }
            flag.isSelected = false
            components.filterIsInstance<com.intellij.ui.components.JBTextArea>().single().text = "first\nsecond"
            dialog.performOKAction()
            assertTrue(submitted.isEmpty()) // Required validation remains active without the star.
            val password = components.filterIsInstance<javax.swing.JPasswordField>().single()
            password.text = "secret-value"
            val maskedChar = password.echoChar
            val visibility = components.filterIsInstance<javax.swing.JToggleButton>().single { it.name == "parameter.passwordVisibility" }
            assertNotNull(visibility.icon)
            visibility.doClick()
            assertEquals('\u0000', password.echoChar)
            assertEquals("隐藏密码", visibility.toolTipText)
            visibility.doClick()
            assertEquals(maskedChar, password.echoChar)
            assertEquals("secret-value", String(password.password))
            // Integer, number, multiline, and secret controls have save-default actions.
            val saveButtons = components.filterIsInstance<javax.swing.JButton>().filter { it.text == "设为默认值" }
            assertEquals(4, saveButtons.size)
            saveButtons.last().doClick()
            assertEquals("secret-value", library.list(false).single { it.id == script.id }.parameters.single { it.id == "令牌" }.defaultValue)
            val fields = components.filterIsInstance<javax.swing.JTextField>().filter { it !is javax.swing.JPasswordField }
            val integer = fields.single { it.text == "3" }
            val number = fields.single { it.text == "0.5" }
            integer.text = "1.5"
            dialog.performOKAction()
            assertTrue(submitted.isEmpty())
            integer.text = "4"
            number.text = "invalid"
            dialog.performOKAction()
            assertTrue(submitted.isEmpty())
            number.text = "0.75"
            dialog.performOKAction()
            assertEquals(mapOf("次数" to "4", "比例" to "0.75", "启用" to "False", "环境" to "dev", "说明" to "first\nsecond", "令牌" to "secret-value"), submitted.single())
        } finally { Disposer.dispose(dialog.disposable); library.loadState(oldState) }
    }

    fun testTemplateRadioAndSecretPreviewUseTheirNativeControls() {
        val dialog = TemplateHelpDialog(project)
        try {
            val builder = dialog.javaClass.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }
            val content = builder.invoke(dialog) as Component
            val list = descendants(content).filterIsInstance<javax.swing.JList<*>>().single()
            val preview = descendants(content).filterIsInstance<com.intellij.ui.components.JBTextArea>().single { it.name == "template.preview" }
            list.selectedIndex = (0 until list.model.size).single { list.model.getElementAt(it).toString() == "单选按钮组" }
            assertEquals("dev", preview.text)
            descendants(content).filterIsInstance<javax.swing.JRadioButton>().single { it.text == "生产" }.doClick()
            assertEquals("prod", preview.text)
            list.selectedIndex = (0 until list.model.size).single { list.model.getElementAt(it).toString() == "密码输入" }
            val password = descendants(content).filterIsInstance<javax.swing.JPasswordField>().single()
            password.text = "private-token"
            descendants(content).filterIsInstance<javax.swing.JToggleButton>().single { it.name == "parameter.passwordVisibility" }.doClick()
            assertEquals('\u0000', password.echoChar)
            assertEquals("******", preview.text)
        } finally { Disposer.dispose(dialog.disposable) }
    }

    fun testExecutionDialogKeepsParametersForRepeatedRuns() {
        val script = ScriptDefinition().also { it.title = "Repeat"; it.executor.command = "echo hello" }
        script.parameters = ParameterTemplates.synchronize(listOf("${'$'}{name:hello}"), emptyList())
        val runs = arrayListOf<Map<String, String>>()
        val dialog = ParameterDialog(project, script, emptyMap(), onExecute = { runs.add(it) })
        try {
            val builder = dialog.javaClass.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }
            val content = builder.invoke(dialog) as Component
            val field = descendants(content).filterIsInstance<javax.swing.JTextField>().single()
            val keepOpen = descendants(content).filterIsInstance<javax.swing.JCheckBox>().single { it.text == "执行后保留弹窗" }
            assertFalse(keepOpen.isSelected)
            keepOpen.isSelected = true
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

    fun testExecutionDialogClosesByDefaultAfterAcceptedRun() {
        val script = ScriptDefinition().also { it.executor.command = "echo hello" }
        script.parameters = ParameterTemplates.synchronize(listOf("${'$'}{name:hello}"), emptyList())
        var submitted: Map<String, String>? = null
        val dialog = ParameterDialog(project, script, emptyMap(), onExecute = { submitted = it; true })
        try {
            dialog.performOKAction()
            assertEquals(mapOf("name" to "hello"), submitted)
            assertTrue(dialog.isOK)
        } finally { Disposer.dispose(dialog.disposable) }
    }

    fun testExecutionDialogStaysOpenWhenRunIsNotAccepted() {
        val script = ScriptDefinition().also { it.executor.command = "echo hello" }
        script.parameters = ParameterTemplates.synchronize(listOf("${'$'}{name:hello}"), emptyList())
        var accepted = false
        val dialog = ParameterDialog(project, script, emptyMap(), onExecute = { accepted })
        try {
            dialog.performOKAction()
            assertFalse(dialog.isOK)
            assertFalse(Disposer.isDisposed(dialog.disposable))
            accepted = true
            dialog.performOKAction()
            assertTrue(dialog.isOK)
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
            val removed = setOf("显示 Android 触摸点 PowerShell", "查看日志目录 PowerShell", "按数量输出序号 Python")
            assertFalse((0 until list.model.size).any { list.model.getElementAt(it).toString() in removed })
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
                val defaults = parameters.associate {
                    val initial = it.defaultValue?.toString() ?: ""
                    val value = when (it.kind) {
                        "boolean" -> if (initial.equals("true", ignoreCase = true) || initial == "1") "True" else "False"
                        "secret" -> if (initial.isNotEmpty()) "******" else ""
                        else -> initial
                    }
                    it.id to value
                }
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
            val actions = panel.globalActions.getChildren(null)
            assertEquals(listOf("环境路径", "帮助", "复制 SKILL"), actions.takeLast(3).map { it.templatePresentation.text })
            val clipboard = com.intellij.openapi.ide.CopyPasteManager.getInstance()
            val oldContents = clipboard.contents
            try {
                val copySkill = actions.last()
                copySkill.actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                    copySkill, null, "NikoTools.ScriptToolbar", com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
                ))
                val expected = requireNotNull(javaClass.getResourceAsStream("/skills/nikotools-script-parameters/SKILL.md"))
                    .reader(Charsets.UTF_8).use { it.readText() }
                assertEquals(expected, clipboard.getContents<String>(java.awt.datatransfer.DataFlavor.stringFlavor))
            } finally { if (oldContents != null) clipboard.setContents(oldContents) }
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
        val b = Files.createDirectory(a.resolve("B"))
        val file = Files.writeString(b.resolve("input.txt"), "input")
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
                field.text = b.toString()
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                field.text = file.toString()
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                field.text = "B/input.txt"
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                field.text = "\"$file\""
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                field.text = "input.txt"
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectB, field).getInitialFile())
                field.text = b.resolve("missing.txt").toString()
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                field.text = b.resolve("missing-directory/deeper/C.txt").toString()
                assertEquals(rootB, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                field.text = a.resolve("missing-B/C.txt").toString()
                assertEquals(rootA, ProjectRootBrowseListener(descriptor, projectB, field).getInitialFile())
                field.text = "missing-B/C.txt"
                assertEquals(rootA, ProjectRootBrowseListener(descriptor, projectA, field).getInitialFile())
                for (invalid in listOf("", "   ", "bad\u0000path")) {
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

    fun testFileChooserExplicitDirectoryOverridesRememberedProjectRoot() {
        val directory = Files.createTempDirectory("niko-chooser-history")
        val b = Files.createDirectory(directory.resolve("B"))
        val file = Files.writeString(b.resolve("C.txt"), "input")
        val fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        val root = requireNotNull(fs.refreshAndFindFileByNioFile(directory))
        val parent = requireNotNull(fs.refreshAndFindFileByNioFile(b))
        val oldSelection = com.intellij.openapi.fileChooser.impl.FileChooserUtil.getLastOpenedFile(project)
        val field = TextFieldWithBrowseButton().apply { text = file.toString() }
        try {
            com.intellij.openapi.fileChooser.impl.FileChooserUtil.setLastOpenedFile(project, root)
            val descriptor = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor()
            descriptor.putUserData(com.intellij.openapi.fileChooser.PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, true)
            val listener = ProjectRootBrowseListener(descriptor, project, field)
            val actualDescriptor = com.intellij.openapi.ui.BrowseFolderRunnable::class.java
                .getDeclaredField("myFileChooserDescriptor").apply { isAccessible = true }.get(listener)
                as com.intellij.openapi.fileChooser.FileChooserDescriptor
            assertEquals(parent, listener.getInitialFile())
            assertEquals(parent, com.intellij.openapi.fileChooser.impl.FileChooserUtil.getFileToSelect(
                actualDescriptor, project, listener.getInitialFile()))
            // Configuring this field must not alter another caller's descriptor.
            assertEquals(true, descriptor.getUserData(com.intellij.openapi.fileChooser.PathChooserDialog.PREFER_LAST_OVER_EXPLICIT))
        } finally {
            com.intellij.openapi.fileChooser.impl.FileChooserUtil.setLastOpenedFile(project, oldSelection)
            field.dispose()
            Files.delete(file); Files.delete(b); Files.delete(directory)
        }
    }

    fun testFileChooserFindsParentOfBuildOutputNotYetInVfs() {
        val directory = Files.createTempDirectory("niko-generated-apk")
        val fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        val parent = requireNotNull(fs.refreshAndFindFileByNioFile(directory))
        val apk = directory.resolve("new-output.apk")
        // Cache the directory while it is empty, then create a build output outside the IDE.
        assertTrue(parent.children.isEmpty())
        Files.writeString(apk, "apk")
        val field = TextFieldWithBrowseButton().apply { text = apk.toString() }
        try {
            assertNull(parent.findChild(apk.fileName.toString()))
            val listener = ProjectRootBrowseListener(
                com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor(), project, field)
            assertEquals(parent, listener.getInitialFile())
        } finally {
            field.dispose()
            Files.delete(apk); Files.delete(directory)
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
