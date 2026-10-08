package com.poptools.scripts

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import java.lang.reflect.Proxy
import org.junit.Assume
import org.junit.Assert.*
import org.junit.Test

/** Exercise the real terminal API shape from each SDK, without starting a terminal process. */
class TerminalCompatibilityTest {
    @Test fun absentTerminalViewUsesFallback() {
        val result = ModernTerminalSelection.read(DataContext { null })
        assertFalse(result.available)
        assertNull(result.text)
    }
    @Test fun modernSelectionPreservesOffsetTypesAndText() {
        val result = ModernTerminalSelection.read(modernContext(false))
        assertTrue(result.available)
        assertEquals("echo 中文", result.text)
    }
    @Test fun emptyModernSelectionDoesNotUseAnotherEditor() {
        val result = ModernTerminalSelection.read(modernContext(true))
        assertTrue(result.available)
        assertNull(result.text)
    }
    private fun proxy(type: Class<*>, values: (String) -> Any?): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
        when (method.name) {
            "toString" -> type.name
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === args[0]
            else -> values(method.name)
        }
    }
    private fun modernContext(empty: Boolean): DataContext {
        val loader = ModernTerminalSelection::class.java.classLoader
        val viewType = try { Class.forName("com.intellij.terminal.frontend.view.TerminalView", false, loader) }
        catch (unavailable: ClassNotFoundException) {
            Assume.assumeNoException("Old terminal has no Reworked API", unavailable)
            throw unavailable
        }
        val modelType = viewType.getMethod("getTextSelectionModel").returnType
        val selectionType = modelType.getMethod("getSelection").returnType
        val offsetType = selectionType.getMethod("getStartOffset").returnType
        val start = offsetType.getMethod("of", java.lang.Long.TYPE).invoke(null, 0L)
        val end = offsetType.getMethod("of", java.lang.Long.TYPE).invoke(null, 7L)
        val selectionCompanion = selectionType.getField("Companion").get(null)
        val selection = selectionCompanion.javaClass.getMethod("of", offsetType, offsetType).invoke(selectionCompanion, start, end)
        val model = proxy(modelType) { if (empty) null else selection }
        val outputsType = viewType.getMethod("getOutputModels").returnType
        val outputType = outputsType.getMethod("getRegular").returnType
        val output = Proxy.newProxyInstance(loader, arrayOf(outputType)) { _, method, args ->
            if (method.name == "getText") { assertSame(start, args[0]); assertSame(end, args[1]); "echo 中文" }
            else null
        }
        val stateType = outputsType.getMethod("getActive").returnType
        val state = proxy(stateType) { output }
        val outputs = proxy(outputsType) { if (it == "getActive") state else output }
        val view = proxy(viewType) { if (it == "getTextSelectionModel") model else outputs }
        val companion = viewType.getField("Companion").get(null)
        val key = companion.javaClass.getMethod("getDATA_KEY").invoke(companion) as DataKey<*>
        return DataContext { id -> if (key.`is`(id)) view else null }
    }
}
