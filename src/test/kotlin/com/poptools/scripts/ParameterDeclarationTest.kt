package com.poptools.scripts

import org.junit.Assert.*
import org.junit.Test

class ParameterDeclarationTest {
    @Test fun lowercaseAndLegacyDeclarationsHaveTheSameParametersAndOutput() {
        for (newline in listOf("\n", "\r\n")) {
            val lower = "var file = ${'$'}{输入文件@file:/tmp/input.txt}${newline}var mode : ${'$'}{模式:开启=1|关闭=0}${newline}echo ${'$'}{file} ${'$'}{mode}"
            val parameters = ParameterTemplates.synchronize(listOf(lower), emptyList())
            assertEquals(listOf("file", "mode"), parameters.map { it.id })
            assertEquals(listOf("输入文件", "模式"), parameters.map { it.label })
            assertEquals(listOf("file", "choice"), parameters.map { it.kind })
            for (keyword in listOf("var", "Var", "pVal")) {
                val source = lower.replace("var ", "$keyword ")
                assertEquals(ScriptJson.GSON.toJson(parameters), ScriptJson.GSON.toJson(ParameterTemplates.synchronize(listOf(source), emptyList())))
                assertEquals("echo /tmp/other.txt 0", ParameterTemplates.render(source, mapOf("file" to "/tmp/other.txt", "mode" to "0")))
                assertFalse(ScriptJson.share(ScriptDefinition().also { it.executor.command = source; it.parameters = parameters }).contains("/tmp/input.txt"))
            }
        }
    }

    @Test fun mixedDeclarationSpellingsShareReferencesAndRejectConflicts() {
        val source = "var a = ${'$'}{名称:hello}\nVar b = ${'$'}{名称:world}\necho ${'$'}{a} ${'$'}{b}"
        assertEquals(listOf("a", "b"), ParameterTemplates.synchronize(listOf(source), emptyList()).map { it.id })
        assertEquals("echo one two", ParameterTemplates.render(source, mapOf("a" to "one", "b" to "two")))
        assertThrows(IllegalArgumentException::class.java) {
            ParameterTemplates.synchronize(listOf("var a = ${'$'}{名:1}\nVar a = ${'$'}{名:2}\n${'$'}{a}"), emptyList())
        }
    }
}
