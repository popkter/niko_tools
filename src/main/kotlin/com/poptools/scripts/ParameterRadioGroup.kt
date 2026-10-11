package com.poptools.scripts

import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JRadioButton

/** Keep the option label in the UI and return its value to the script. */
internal class ParameterRadioGroup(options: List<ScriptDefinition.Option>, initial: String, onChange: () -> Unit = {}) : JPanel() {
    private val buttons = options.map { it to JRadioButton(it.label) }

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        val group = ButtonGroup()
        val selected = options.find { it.value == initial } ?: options.firstOrNull()
        for ((option, button) in buttons) {
            button.isOpaque = false
            group.add(button)
            button.isSelected = option == selected
            button.addActionListener { onChange() }
            add(button)
        }
    }

    fun value(): String = buttons.firstOrNull { it.second.isSelected }?.first?.value ?: ""
}
