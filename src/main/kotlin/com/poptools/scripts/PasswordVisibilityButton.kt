package com.poptools.scripts

import com.intellij.icons.AllIcons
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.JBUI
import javax.swing.JPasswordField
import javax.swing.JToggleButton

/** Changing the echo character keeps the same field, value, and document bindings. */
internal fun Row.passwordVisibilityButton(field: JPasswordField) {
    val maskedChar = field.echoChar
    cell(JToggleButton(AllIcons.Actions.Show).apply {
        name = "parameter.passwordVisibility"
        margin = JBUI.insets(2)
        toolTipText = "显示密码"
        accessibleContext.accessibleName = toolTipText
        addActionListener {
            field.echoChar = if (isSelected) '\u0000' else maskedChar
            toolTipText = if (isSelected) "隐藏密码" else "显示密码"
            accessibleContext.accessibleName = toolTipText
        }
    })
}
