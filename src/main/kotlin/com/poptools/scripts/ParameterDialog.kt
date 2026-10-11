package com.poptools.scripts

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import java.awt.Dimension
import java.math.BigDecimal
import java.math.BigInteger
import javax.swing.JComponent
import javax.swing.JCheckBox

class ParameterDialog @JvmOverloads constructor(
    private val project: Project,
    script: ScriptDefinition,
    private val previous: Map<String, String>,
    buttonText: String = "执行",
    private val onExecute: ((Map<String, String>) -> Boolean)? = null
) : DialogWrapper(project) {
    private val parameters = script.parameters
    private val scriptId = script.id
    private val allowSaveDefaults = buttonText == "执行"
    private val values = linkedMapOf<String, String>()
    private val readers = linkedMapOf<String, () -> String>()
    private lateinit var form: DialogPanel
    private var keepOpen: JCheckBox? = null
    init {
        title = "运行参数 — ${script.title}"
        setOKButtonText(buttonText)
        if (onExecute != null) { isModal = false; setCancelButtonText("关闭") }
        init()
        form.registerValidators(disposable)
    }

    override fun createCenterPanel(): JComponent {
        form = panel {
        for (p in parameters) {
            val initial = previous.getOrDefault(p.id, p.defaultValue?.toString() ?: "")
            row(p.label ?: p.id) {
                when (p.kind) {
                    "choice" -> comboBox(p.options).align(AlignX.FILL).resizableColumn().apply {
                        component.selectedItem = p.options.find { it.value == initial } ?: p.options.firstOrNull()
                        readers[p.id] = { (component.selectedItem as ScriptDefinition.Option).value }
                    }
                    "radio" -> cell(ParameterRadioGroup(p.options, initial)).align(AlignX.FILL).resizableColumn().apply {
                        readers[p.id] = { component.value() }
                    }
                    "boolean" -> checkBox("").apply {
                        component.isSelected = initial.equals("true", ignoreCase = true) || initial == "1"
                        readers[p.id] = { if (component.isSelected) "True" else "False" }
                    }
                    "file", "directory" -> projectPathField(project = project, descriptor =
                        if (p.kind == "file") FileChooserDescriptorFactory.createSingleFileDescriptor() else FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    ).align(AlignX.FILL).resizableColumn().apply {
                        component.text = initial; readers[p.id] = { component.text }
                    }
                    "multiline" -> scrollCell(JBTextArea(initial, 5, 45)).align(Align.FILL).resizableColumn().apply {
                        readers[p.id] = { component.text }
                    }
                    "secret" -> {
                        val field = passwordField().align(AlignX.FILL).resizableColumn().apply {
                            component.text = initial; readers[p.id] = { String(component.password) }
                        }
                        passwordVisibilityButton(field.component)
                    }
                    "android_device" -> textField().align(AlignX.FILL).resizableColumn().apply {
                        component.text = if (p.required) AndroidDevices.selectedSerial(project) else AndroidDevices.selectedSerialOrNull(project) ?: ""
                        component.isEditable = false
                        readers[p.id] = { component.text }
                    }
                    else -> textField().align(AlignX.FILL).resizableColumn().apply {
                        component.text = initial; readers[p.id] = { component.text }
                        validationOnApply { field ->
                            try { validateValue(p, field.text); null }
                            catch (e: IllegalArgumentException) { ValidationInfo(e.message ?: "参数无效", field) }
                        }
                    }
                }
                if (allowSaveDefaults && p.kind !in setOf("boolean", "choice", "radio", "android_device")) {
                    button("设为默认值") {
                        try { ScriptLibrary.getInstance().setParameterDefault(scriptId, p.id, readers.getValue(p.id)()); setErrorText(null) }
                        catch (error: Exception) { setErrorText(error.message) }
                    }
                }
            }
        }
        if (onExecute != null) row {
            keepOpen = checkBox("执行后保留弹窗").component
        }
        }
        return JBScrollPane(form).apply { preferredSize = Dimension(680, (parameters.size * 55).coerceIn(130, 550)) }
    }

    private fun validateValue(p: ScriptDefinition.Parameter, value: String) {
        require(!p.required || value.isNotBlank()) { "请填写：${p.label}" }
        if (value.isNotBlank() && p.kind == "integer") BigInteger(value)
        if (value.isNotBlank() && p.kind == "number") BigDecimal(value)
    }
    override fun doOKAction() {
        try {
            values.clear()
            for (p in parameters) {
                val value = readers.getValue(p.id)()
                validateValue(p, value); values[p.id] = value
            }
            setErrorText(null)
            if (onExecute != null) {
                if (onExecute.invoke(values()) && keepOpen?.isSelected != true) close(OK_EXIT_CODE)
            } else super.doOKAction()
        } catch (e: Exception) { setErrorText(e.message ?: "参数无效") }
    }
    fun values(): Map<String, String> = LinkedHashMap(values)
}
