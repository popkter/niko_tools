package com.poptools.scripts

import com.google.gson.JsonParser
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JLabel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

/** Reuses PopTool's reference snippets, without introducing a user-maintained template library. */
class TemplateHelpDialog(private val project: Project) : DialogWrapper(project) {
    private data class Example(val title: String, val body: String, val code: String) { override fun toString() = title }
    private val examples = loadExamples()
    private var selected = examples.firstOrNull()
    private lateinit var description: JBTextArea
    private lateinit var code: JBTextArea
    private lateinit var preview: JBTextArea
    private lateinit var previewStatus: JLabel
    private val parameterHost = JPanel(BorderLayout())
    private var parameters = emptyList<ScriptDefinition.Parameter>()
    private val readers = linkedMapOf<String, () -> String>()
    init { title = "脚本模板与参数语法"; setOKButtonText("关闭"); init() }

    private fun loadExamples(): List<Example> = try {
        requireNotNull(javaClass.getResourceAsStream("/poptool-guide.json")).reader(Charsets.UTF_8).use { reader ->
            buildList {
                for (section in JsonParser.parseReader(reader).asJsonArray) {
                    val o = section.asJsonObject
                    if (o["id"].asString !in setOf("syntax", "templates")) continue
                    for (card in o.getAsJsonArray("cards")) {
                        val c = card.asJsonObject
                        if (c.has("code")) add(Example(c["title"].asString, c["body"].asString, c["code"].asString))
                    }
                }
            }
        }
    } catch (error: Exception) { throw IllegalStateException("无法读取 PopTool 模板示例", error) }

    private fun updateExample() {
        val example = selected ?: return
        description.text = example.body; description.caretPosition = 0
        code.text = example.code; code.caretPosition = 0
        parameters = ParameterTemplates.synchronize(listOf(example.code), emptyList())
        readers.clear()
        val form = panel {
            for (parameter in parameters) {
                val initial = parameter.defaultValue?.toString() ?: ""
                row(parameter.label ?: parameter.id) {
                    when (parameter.kind) {
                        "choice" -> comboBox(parameter.options).align(AlignX.FILL).resizableColumn().apply {
                            component.selectedItem = parameter.options.find { it.value == initial } ?: parameter.options.firstOrNull()
                            readers[parameter.id] = { (component.selectedItem as? ScriptDefinition.Option)?.value ?: "" }
                            component.addActionListener { updatePreview() }
                        }
                        "radio" -> cell(ParameterRadioGroup(parameter.options, initial) { updatePreview() })
                            .align(AlignX.FILL).resizableColumn().apply {
                                readers[parameter.id] = { component.value() }
                            }
                        "boolean" -> checkBox("").apply {
                            component.isSelected = initial.equals("true", ignoreCase = true) || initial == "1"
                            readers[parameter.id] = { if (component.isSelected) "True" else "False" }
                            component.addActionListener { updatePreview() }
                        }
                        "multiline" -> scrollCell(JBTextArea(initial, 5, 45)).align(Align.FILL).resizableColumn().apply {
                            readers[parameter.id] = { component.text }
                            watchPreview(component)
                        }
                        "secret" -> {
                            val field = passwordField().align(AlignX.FILL).resizableColumn().apply {
                                component.text = initial
                                readers[parameter.id] = { String(component.password) }
                                watchPreview(component)
                            }
                            passwordVisibilityButton(field.component)
                        }
                        "file", "directory" -> projectPathField(project, if (parameter.kind == "file")
                            FileChooserDescriptorFactory.createSingleFileDescriptor() else FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        ).align(AlignX.FILL).resizableColumn().apply {
                            component.text = initial
                            readers[parameter.id] = { component.text }
                            watchPreview(component.textField)
                        }
                        else -> textField().align(AlignX.FILL).resizableColumn().apply {
                            component.text = initial
                            readers[parameter.id] = { component.text }
                            watchPreview(component)
                        }
                    }
                }
            }
            if (parameters.isEmpty()) row { comment("此模板无需填写参数，可直接预览。") }
        }
        parameterHost.removeAll()
        parameterHost.add(form, BorderLayout.CENTER)
        parameterHost.revalidate(); parameterHost.repaint()
        updatePreview()
    }

    private fun watchPreview(field: JTextComponent) {
        field.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = updatePreview()
            override fun removeUpdate(event: DocumentEvent) = updatePreview()
            override fun changedUpdate(event: DocumentEvent) = updatePreview()
        })
    }

    private fun updatePreview() {
        val example = selected ?: return
        try {
            val values = parameters.associate { parameter ->
                val value = readers.getValue(parameter.id)()
                require(!parameter.required || value.isNotBlank()) { "请填写：${parameter.label ?: parameter.id}" }
                if (value.isNotBlank() && parameter.kind == "integer") java.math.BigInteger(value)
                if (value.isNotBlank() && parameter.kind == "number") java.math.BigDecimal(value)
                parameter.id to if (parameter.kind == "secret" && value.isNotEmpty()) "******" else value
            }
            preview.text = ParameterTemplates.render(example.code, values)
            preview.caretPosition = 0
            previewStatus.text = "替换结果仅用于预览，不执行脚本。"
        } catch (error: IllegalArgumentException) {
            preview.text = ""
            previewStatus.text = error.message ?: "参数无效"
        }
    }
    override fun createCenterPanel(): JComponent {
        val exampleList = JBList(examples).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            accessibleContext.accessibleName = "参数模板列表"
        }
        val listPanel = panel {
            row { label("参数模板") }
            row { scrollCell(exampleList).align(Align.FILL).resizableColumn() }.resizableRow()
        }
        val codePanel = panel {
            row { label("说明与写法") }
            row {
                description = JBTextArea(selected?.body ?: "", 2, 40).apply {
                    name = "template.description"; isEditable = false; lineWrap = true; wrapStyleWord = true
                }
                cell(description).align(AlignX.FILL).resizableColumn()
            }
            row {
                code = JBTextArea(selected?.code ?: "", 8, 40).apply {
                    name = "template.code"; isEditable = false; font = Font(Font.MONOSPACED, Font.PLAIN, 13)
                }
                scrollCell(code).align(Align.FILL).resizableColumn()
            }.resizableRow()
        }
        val previewPanel = panel {
            row { label("参数与替换预览") }
            row { scrollCell(parameterHost).align(Align.FILL).resizableColumn() }.resizableRow()
            row { previewStatus = label("").component }
            row {
                preview = JBTextArea("", 5, 40).apply {
                    name = "template.preview"; isEditable = false; font = Font(Font.MONOSPACED, Font.PLAIN, 13)
                }
                scrollCell(preview).align(Align.FILL).resizableColumn()
            }.resizableRow()
        }
        val sections = OnePixelSplitter(true, 0.48f).apply {
            firstComponent = codePanel
            secondComponent = previewPanel
        }
        val detailPanel = panel {
            row { cell(sections).align(Align.FILL).resizableColumn() }.resizableRow()
            row {
                button("复制代码") { CopyPasteManager.getInstance().setContents(StringSelection(code.text)) }
                button("复制预览结果") { CopyPasteManager.getInstance().setContents(StringSelection(preview.text)) }
            }
        }
        exampleList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                selected = exampleList.selectedValue
                updateExample()
            }
        }
        exampleList.selectedIndex = examples.indexOf(selected).takeIf { it >= 0 } ?: -1
        return OnePixelSplitter(false, 0.28f).apply {
            listPanel.minimumSize = Dimension(180, 0)
            detailPanel.minimumSize = Dimension(360, 0)
            firstComponent = listPanel
            secondComponent = detailPanel
            preferredSize = Dimension(1000, 700)
        }
    }
}
