package com.poptools.scripts

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.Logger

/** Keep newer Reworked Terminal classes out of the baseline platform's class linkage. */
object ModernTerminalSelection {
    private val log = Logger.getInstance(ModernTerminalSelection::class.java)
    data class Result(val available: Boolean, val text: String?)
    @JvmStatic fun read(context: DataContext): Result {
        try {
            val loader = javaClass.classLoader
            val viewType = Class.forName("com.intellij.terminal.frontend.view.TerminalView", false, loader)
            val companion = viewType.getField("Companion").get(null)
            val key = companion.javaClass.getMethod("getDATA_KEY").invoke(companion) as DataKey<*>
            val view = key.getData(context) ?: return Result(false, null)
            val modelGetter = viewType.getMethod("getTextSelectionModel")
            val model = modelGetter.invoke(view)
            val selectionGetter = modelGetter.returnType.getMethod("getSelection")
            val selection = selectionGetter.invoke(model) ?: return Result(true, null)
            val startGetter = selectionGetter.returnType.getMethod("getStartOffset")
            val endGetter = selectionGetter.returnType.getMethod("getEndOffset")
            val start = startGetter.invoke(selection)
            val end = endGetter.invoke(selection)
            val helpers = Class.forName("com.intellij.terminal.frontend.view.TerminalViewKt", false, loader)
            val outputGetter = helpers.getMethod("activeOutputModel", viewType)
            val output = outputGetter.invoke(null, view)
            val text = outputGetter.returnType.getMethod("getText", startGetter.returnType, endGetter.returnType).invoke(output, start, end)
            return Result(true, text.toString())
        } catch (_: ClassNotFoundException) {
            return Result(false, null)
        } catch (error: ReflectiveOperationException) {
            log.debug("Reworked Terminal API is unavailable; trying classic/editor selection", error)
            return Result(false, null)
        } catch (error: LinkageError) {
            log.debug("Reworked Terminal API is unavailable; trying classic/editor selection", error)
            return Result(false, null)
        }
    }
}
