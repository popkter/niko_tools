package com.poptools.scripts

import com.google.gson.reflect.TypeToken
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent

class ScriptEditor(private val project: Project, source: ScriptDefinition, private val template: Boolean) : DialogWrapper(project) {
    private val script = ScriptJson.copy(source)
    private val originalTemplates = ParameterTemplates.templates(source)
    private var scriptTitle = script.title
    private var description = script.description
    private var kind = script.executor.kind
    private var cwd = script.executor.cwd ?: ""
    private var interpreter = script.interpreter
    private var requirements = script.executor.requirements.joinToString(", ")
    private var encoding = script.executor.encoding
    private var timeout = script.executor.timeout_seconds?.toString() ?: ""
    private var command = script.executor.command
    private var arguments = ScriptJson.GSON.toJson(script.executor.args)
    private var environment = ScriptJson.GSON.toJson(script.executor.env)
    private var parameters = ScriptJson.GSON.toJson(script.parameters)
    private lateinit var form: DialogPanel
    private lateinit var body: JBTextArea

    init {
        title = if (template) "编辑脚本模板" else "编辑自定义脚本"
        setOKButtonText("保存"); init(); form.registerValidators(disposable)
    }
    override fun createCenterPanel(): JComponent {
        form = panel {
            row("名称") {
                textField().bindText(::scriptTitle).align(AlignX.FILL).resizableColumn()
                    .validationOnApply { if (it.text.isBlank()) ValidationInfo("脚本名称不能为空", it) else null }
            }
            row("说明") { textField().bindText(::description).align(AlignX.FILL).resizableColumn() }
            row("执行类型") { comboBox(listOf("powershell", "python", "bash", "batch", "process")).bindItem({ kind }, { kind = requireNotNull(it) }) }
            row {
                body = JBTextArea(command, 14, 75).apply { font = Font(Font.MONOSPACED, Font.PLAIN, 13) }
                scrollCell(body).bindText(::command).align(Align.FILL).resizableColumn().focused()
                    .validationOnApply { if (it.text.isBlank()) ValidationInfo("脚本正文不能为空", it) else null }
            }.resizableRow()
            row {
                comment("\${参数:默认值} · \${模式@choice:开启=1|关闭=0} · \${文件@file} · var id = \${显示名称}")
                link("脚本模板与参数语法") { TemplateHelpDialog(project).show() }
            }
            collapsibleGroup("执行配置") {
                row("工作目录") {
                    projectPathField(project = project, descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor())
                        .bindText(::cwd).align(AlignX.FILL).resizableColumn().comment("留空使用当前项目根目录")
                }
                row("解释器路径") {
                    projectPathField(project = project, descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor())
                        .bindText(::interpreter).align(AlignX.FILL).resizableColumn().comment("留空使用全局设置或系统环境")
                }
                row("依赖工具") { textField().bindText(::requirements).align(AlignX.FILL).resizableColumn().comment("使用逗号分隔") }
                row("输出编码") { textField().bindText(::encoding) }
                row("超时秒数") {
                    textField().bindText(::timeout).comment("留空为不限")
                        .validationOnApply { field -> if (field.text.isNotBlank() && (field.text.trim().toIntOrNull() ?: 0) < 1) ValidationInfo("超时必须大于零", field) else null }
                }
                row("参数数组 JSON") { scrollCell(JBTextArea(arguments, 4, 65)).bindText(::arguments).align(Align.FILL).resizableColumn() }
                row("环境变量 JSON") { scrollCell(JBTextArea(environment, 4, 65)).bindText(::environment).align(Align.FILL).resizableColumn() }
            }
            collapsibleGroup("参数元数据") {
                row { comment("保留 PopTool 类型、必填、默认值及选项。新占位符在保存时生成。") }
                row { scrollCell(JBTextArea(parameters, 9, 65)).bindText(::parameters).align(Align.FILL).resizableColumn() }
            }
        }
        return JBScrollPane(form).apply { preferredSize = Dimension(850, 620) }
    }
    override fun doOKAction() {
        try {
            val errors = form.validateAll()
            if (errors.isNotEmpty()) { setErrorText(errors.first().message); return }
            form.apply()
            script.title = scriptTitle.trim(); script.description = description; script.executor.command = command
            script.executor.kind = kind
            script.executor.cwd = cwd.trim().takeIf { it.isNotEmpty() }; script.interpreter = interpreter.trim()
            script.executor.encoding = encoding.trim(); script.executor.timeout_seconds = timeout.trim().takeIf { it.isNotEmpty() }?.toInt()
            require(script.executor.timeout_seconds?.let { it >= 1 } != false) { "超时必须大于零" }
            script.executor.requirements = requirements.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            script.executor.args = requireNotNull(ScriptJson.GSON.fromJson<MutableList<String>>(arguments, object : TypeToken<MutableList<String>>() {}.type)) { "JSON 不能为 null" }
            script.executor.env = requireNotNull(ScriptJson.GSON.fromJson<MutableMap<String, String>>(environment, object : TypeToken<MutableMap<String, String>>() {}.type)) { "JSON 不能为 null" }
            val existing = requireNotNull(ScriptJson.GSON.fromJson<List<ScriptDefinition.Parameter>>(parameters, object : TypeToken<List<ScriptDefinition.Parameter>>() {}.type)) { "JSON 不能为 null" }
            val derived = ParameterTemplates.synchronize(ParameterTemplates.templates(script), existing)
            if (originalTemplates == ParameterTemplates.templates(script)) {
                for (p in derived) existing.find { it.id == p.id }?.let { p.defaultValue = it.defaultValue }
            }
            script.parameters = derived; script.revision++
            ScriptLibrary.getInstance().save(script, template); super.doOKAction()
        } catch (e: Exception) { setErrorText(e.message ?: "配置格式无效") }
    }
    override fun getPreferredFocusedComponent(): JComponent = body
}
