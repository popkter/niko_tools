package com.poptools.scripts

import com.google.gson.JsonParser
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent

/** Reuses PopTool's reference snippets, without introducing a user-maintained template library. */
class TemplateHelpDialog(private val project: Project) : DialogWrapper(project) {
    private data class Example(val title: String, val body: String, val code: String) { override fun toString() = title }
    private val examples = loadExamples()
    private var selected = examples.firstOrNull()
    private lateinit var description: JBTextArea
    private lateinit var code: JBTextArea
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
        description.text = example.body; code.text = example.code; code.caretPosition = 0
    }
    override fun createCenterPanel(): JComponent = panel {
        row {
            comboBox(examples).align(AlignX.FILL).resizableColumn().apply {
                component.selectedItem = selected
                component.addActionListener { selected = component.selectedItem as? Example; updateExample() }
            }
        }
        row {
            description = JBTextArea(selected?.body ?: "", 3, 65).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
            cell(description).align(AlignX.FILL).resizableColumn()
        }
        row {
            code = JBTextArea(selected?.code ?: "", 18, 65).apply { isEditable = false; font = Font(Font.MONOSPACED, Font.PLAIN, 13) }
            scrollCell(code).align(Align.FILL).resizableColumn()
        }.resizableRow()
        row {
            button("预览参数替换") {
                val example = selected ?: return@button
                val script = ScriptDefinition().also { it.title = example.title; it.executor.command = example.code }
                script.parameters = ParameterTemplates.synchronize(listOf(example.code), emptyList())
                val dialog = ParameterDialog(project, script, emptyMap(), "预览")
                dialog.title = "模板参数预览"
                if (dialog.showAndGet()) code.text = ParameterTemplates.render(example.code, dialog.values())
            }
            button("复制代码") { CopyPasteManager.getInstance().setContents(StringSelection(code.text)) }
        }
    }.apply { preferredSize = Dimension(820, 550) }
}
