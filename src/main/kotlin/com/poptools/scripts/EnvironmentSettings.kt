package com.poptools.scripts

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.*
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class EnvironmentSettings : BoundConfigurable("NikoTools") {
    override fun createPanel(): DialogPanel {
        val paths = ScriptLibrary.getInstance().paths().toMutableMap()
        return panel {
            for (name in listOf("python", "powershell", "bash", "cmd", "adb", "scrcpy")) {
                row("$name 路径") {
                    projectPathField(descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor())
                        .bindText({ ScriptLibrary.getInstance().paths().getOrDefault(name, "") }, { paths[name] = it })
                        .align(AlignX.FILL).resizableColumn()
                        .validationOnApply { field ->
                            val value = field.text.trim()
                            if (value.isEmpty()) null else try {
                                if (Files.isRegularFile(Path.of(value))) null else ValidationInfo("$name 路径不存在：$value", field)
                            } catch (_: InvalidPathException) { ValidationInfo("$name 路径无效", field) }
                        }
                }
            }
            row { comment("留空使用系统环境。指定路径无效时直接提示，不自动回退。") }
            onApply { ScriptLibrary.getInstance().setPaths(paths.filterValues { it.isNotBlank() }.mapValues { it.value.trim() }) }
        }
    }
}
