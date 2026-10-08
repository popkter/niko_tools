package com.poptools.scripts

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

object AndroidDevices {
    /** Only an explicit device dependency requires a selection; never infer it from command text. */
    @JvmStatic fun needed(s: ScriptDefinition): Boolean = "android_device" in s.executor.requirements ||
        s.parameters.any { it.kind == "android_device" && it.required }

    @JvmStatic fun serialForScript(project: Project?, script: ScriptDefinition): String? =
        if (needed(script)) selectedSerial(project) else selectedSerialOrNull(project)

    /** Optional integration must not prevent ordinary scripts from starting. */
    @JvmStatic fun selectedSerialOrNull(project: Project?): String? = try {
        selectedSerial(project).takeIf { it.isNotBlank() }
    } catch (_: IllegalArgumentException) { null }

    @JvmStatic fun selectedSerial(project: Project?): String {
        try {
            val provider = ApplicationManager.getApplication().getService(AndroidDeviceProvider::class.java)
                ?: throw IllegalArgumentException("当前 IDE 未启用 Android 插件，无法读取 IDE 所选设备。请启用 Android 插件，或移除 android_device 参数/依赖并在脚本中显式指定设备序列号。")
            return provider.selectedSerial(project)
        } catch (error: LinkageError) {
            throw IllegalArgumentException("当前 Android 插件的设备选择 API 不兼容。请移除 android_device 参数/依赖，并在脚本中显式指定设备序列号。", error)
        }
    }
    data class Device(val serial: String, val online: Boolean, val state: String)
    @JvmStatic fun serialFromSelectedDevices(selected: List<Device?>): String {
        require(selected.isNotEmpty()) { "请在 IDE 运行工具栏选择 Android 设备" }
        require(selected.size == 1) { "当前选择了多个设备，请在运行工具栏选择单个目标设备" }
        val device = requireNotNull(selected.first()) { "所选设备尚未连接或设备状态尚未就绪" }
        require(device.online) { "所选设备不可用：${device.state}（请检查连接和 USB 调试授权）" }
        return device.serial
    }
}
