package com.poptools.scripts

import com.intellij.openapi.project.Project
import java.lang.reflect.InvocationTargetException

/** The optional Android API can vary independently of the IntelliJ Platform version. */
class AndroidPluginDeviceProvider : AndroidDeviceProvider {
    override fun selectedSerial(project: Project?): String {
        try {
            val service = Class.forName("com.android.tools.idea.run.deployment.selector.DevicesSelectedService", true, javaClass.classLoader)
            val instance = service.getMethod("getInstance", Project::class.java).invoke(null, project)
            val targets = service.getMethod("getSelectedTargets").invoke(instance) as Collection<*>
            val devices = targets.map { target ->
                requireNotNull(target)
                val selected = target.javaClass.getMethod("getDevice").invoke(target)
                val device = selected?.javaClass?.getMethod("getDdmlibDevice")?.invoke(selected)
                device?.let {
                    AndroidDevices.Device(
                        it.javaClass.getMethod("getSerialNumber").invoke(it) as String,
                        it.javaClass.getMethod("isOnline").invoke(it) as Boolean,
                        it.javaClass.getMethod("getState").invoke(it).toString()
                    )
                }
            }
            return AndroidDevices.serialFromSelectedDevices(devices)
        } catch (error: ReflectiveOperationException) {
            throw incompatible(if (error is InvocationTargetException) error.cause ?: error else error)
        } catch (error: LinkageError) {
            throw incompatible(error)
        } catch (error: ClassCastException) {
            throw incompatible(error)
        }
    }
    private fun incompatible(cause: Throwable) = IllegalArgumentException("当前 Android 插件的设备选择 API 不兼容，请更新 Android 插件，或移除 android_device 参数/依赖后显式指定设备。", cause)
}
