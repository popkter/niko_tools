package com.poptools.scripts

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.components.JBTextField
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class EnvironmentSettings @JvmOverloads constructor(
    private val environment: Map<String, String> = System.getenv()
) : BoundConfigurable("NikoTools") {
    override fun createPanel(): DialogPanel {
        val paths = ScriptLibrary.getInstance().paths().toMutableMap()
        return panel {
            for (name in listOf("python", "powershell", "bash", "cmd", "adb", "scrcpy")) {
                val defaultPath = try { EnvironmentResolver(emptyMap(), environment).resolve(name, "").toString() }
                    catch (_: IllegalArgumentException) { null }
                row("$name 路径") {
                    projectPathField(descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor())
                        .bindText({ ScriptLibrary.getInstance().paths().getOrDefault(name, "") }, { paths[name] = it })
                        .align(AlignX.FILL).resizableColumn()
                        .applyToComponent { (textField as? JBTextField)?.emptyText?.text = defaultPath ?: "未检测到默认环境，请指定路径" }
                        .comment(defaultPath?.let { "默认环境：$it" } ?: "默认环境：未检测到 $name")
                        .validationOnApply { field ->
                            val value = field.text.trim()
                            if (value.isEmpty()) null else try {
                                if (Files.isRegularFile(Path.of(value))) null else ValidationInfo("$name 路径不存在：$value", field)
                            } catch (_: InvalidPathException) { ValidationInfo("$name 路径无效", field) }
                        }
                }
            }
            row { comment("留空使用显示的默认环境路径，灰色路径不会保存为手动配置。Windows 下 Bash 默认使用 Git Bash。指定路径无效时直接提示，不自动回退。") }
            onApply { ScriptLibrary.getInstance().setPaths(paths.filterValues { it.isNotBlank() }.mapValues { it.value.trim() }) }
        }
    }
}
