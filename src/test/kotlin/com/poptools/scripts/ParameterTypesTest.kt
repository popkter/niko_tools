package com.poptools.scripts

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ParameterTypesTest {
    private fun template(content: String) = "\${$content}"

    @Test fun explicitTypesPreserveDefaultsAndLiteralValues() {
        val cases = listOf(
            Triple("text", "A=1|B=2", "text"), Triple("multiline", "first\nsecond", "multiline"),
            Triple("integer", "3", "integer"), Triple("number", "0.5", "number"),
            Triple("boolean", "true", "boolean"), Triple("file", "C:\\中文 空格\\input.txt", "file"),
            Triple("dir", "./output", "directory"), Triple("secret", "a=b:c", "secret")
        )
        for ((type, initial, kind) in cases) {
            val source = template("参数@$type:$initial")
            val parameter = ParameterTemplates.synchronize(listOf(source), emptyList()).single()
            assertEquals(kind, parameter.kind)
            assertEquals(initial, parameter.defaultValue)
            assertEquals(initial, ParameterTemplates.render(source, emptyMap()))
            assertEquals("new\n$\\", ParameterTemplates.render(source, mapOf("参数" to "new\n$\\")))
            val stripped = ParameterTemplates.stripDefaults(source)
            assertEquals(template("参数@$type"), stripped)
            assertEquals(kind, ParameterTemplates.synchronize(listOf(stripped), emptyList()).single().kind)
        }
    }

    @Test fun explicitSelectionsUseLabelsAndValuesAndAllowOneOption() {
        for (type in listOf("choice", "radio")) {
            val source = template("环境@$type:开发=dev|生产=prod")
            val p = ParameterTemplates.synchronize(listOf(source), emptyList()).single()
            assertEquals(type, p.kind)
            assertEquals("dev", p.defaultValue)
            assertEquals(listOf("开发", "生产"), p.options.map { it.label })
            assertEquals(listOf("dev", "prod"), p.options.map { it.value })
            assertEquals("prod", ParameterTemplates.render(source, mapOf("环境" to "prod")))
            assertEquals(source, ParameterTemplates.stripDefaults(source))
            assertEquals(1, ParameterTemplates.synchronize(listOf(template("环境@$type:开发=dev")), emptyList()).single().options.size)
            for (bad in listOf("环境@$type", "环境@$type:", "环境@$type:dev|prod", "环境@$type:A=1|A=2", "环境@$type:A=1|B=")) {
                assertThrows(IllegalArgumentException::class.java) { ParameterTemplates.synchronize(listOf(template(bad)), emptyList()) }
            }
        }
    }

    @Test fun oldTemplatesAndMetadataRemainCompatibleWhileExplicitTextOverridesMetadata() {
        for (source in listOf("模式:开启=1|关闭=0", "on=1|off=0")) {
            assertEquals("choice", ParameterTemplates.synchronize(listOf(template(source)), emptyList()).single().kind)
        }
        assertEquals("hello", ParameterTemplates.render(template("旧格式=hello"), emptyMap()))
        val existing = ScriptDefinition.Parameter().also { it.id = "值"; it.kind = "integer"; it.required = false; it.label = "旧标签" }
        assertEquals("integer", ParameterTemplates.synchronize(listOf(template("值:3")), listOf(existing)).single().kind)
        val explicit = ParameterTemplates.synchronize(listOf(template("值@text:3")), listOf(existing)).single()
        assertEquals("text", explicit.kind)
        assertEquals("旧标签", explicit.label)
        assertFalse(explicit.required)
        for (sources in listOf(listOf("值:3", "值@text:3"), listOf("值@text:3", "值:3"))) {
            assertEquals("text", ParameterTemplates.synchronize(sources.map(::template), listOf(existing)).single().kind)
        }
    }

    @Test fun declarationsReferencesAndConflictingTypesWorkForNewControls() {
        for (keyword in listOf("var", "Var", "pVal")) {
            val source = "$keyword mode = " + template("环境@radio:开发=dev|生产=prod") + "\necho " + template("mode")
            val p = ParameterTemplates.synchronize(listOf(source), emptyList()).single()
            assertEquals("mode", p.id)
            assertEquals("环境", p.label)
            assertEquals("radio", p.kind)
            assertEquals("echo prod", ParameterTemplates.render(source, mapOf("mode" to "prod")))
        }
        for (sources in listOf(listOf("次数", "次数@integer:3"), listOf("次数@integer:3", "次数"))) {
            assertEquals("integer", ParameterTemplates.synchronize(sources.map(::template), emptyList()).single().kind)
        }
        for (sources in listOf(listOf("次数@integer", "次数@number:3"), listOf("次数@integer:3", "次数@text"))) {
            assertThrows(IllegalArgumentException::class.java) { ParameterTemplates.synchronize(sources.map(::template), emptyList()) }
        }
    }

    @Test fun sharingAndImportRetainNewTypesAndRemovePrivateDefaults() {
        val script = ScriptDefinition().also {
            it.executor.command = template("环境@radio:开发=dev|生产=prod") + " " + template("令牌@secret:private-token")
            it.executor.args.add(template("次数@integer:3"))
            it.executor.env["FLAG"] = template("启用@boolean:true")
            it.executor.cwd = template("目录@dir:./private")
            it.parameters = ParameterTemplates.synchronize(ParameterTemplates.templates(it), emptyList())
        }
        ScriptJson.validate(script)
        val shared = ScriptJson.share(script)
        assertFalse(shared.contains("private-token"))
        assertFalse(shared.contains("./private"))
        val imported = ScriptJson.decode(JsonParser.parseString(shared))
        assertEquals(listOf("radio", "secret", "integer", "boolean", "directory"), imported.parameters.map { it.kind })
        assertEquals(2, imported.parameters.first().options.size)
        val emptyRadio = ScriptDefinition().also {
            it.executor.command = "echo hello"
            it.parameters.add(ScriptDefinition.Parameter().also { p -> p.id = "环境"; p.kind = "radio" })
        }
        assertThrows(IllegalArgumentException::class.java) { ScriptJson.validate(emptyRadio) }
    }
}
