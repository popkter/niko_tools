package com.poptools.scripts

import com.google.gson.*
import java.nio.charset.Charset

object ScriptJson {
    // Field naming keeps the original PopTool "default" property.
    @JvmField val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .setFieldNamingStrategy { if (it.name == "defaultValue") "default" else it.name }
        .registerTypeAdapter(ScriptDefinition.Option::class.java, JsonDeserializer { json, _, _ ->
            if (json.isJsonPrimitive) ScriptDefinition.Option(json.asString, json.asString)
            else json.asJsonObject.let { ScriptDefinition.Option(it["label"].asString, it["value"].asString) }
        } as JsonDeserializer<ScriptDefinition.Option>).create()
    private val kinds = setOf("powershell", "python", "bash", "batch", "process")
    private val parameterKinds = setOf("text", "multiline", "integer", "number", "boolean", "choice", "file", "directory", "android_device", "secret")

    @JvmStatic fun copy(s: ScriptDefinition): ScriptDefinition = GSON.fromJson(GSON.toJson(s), ScriptDefinition::class.java)
    @JvmStatic fun copyParameter(p: ScriptDefinition.Parameter): ScriptDefinition.Parameter = GSON.fromJson(GSON.toJson(p), ScriptDefinition.Parameter::class.java)

    // Gson can populate null even in a non-null Kotlin field; validate before any dereference.
    @Suppress("SENSELESS_COMPARISON")
    @JvmStatic fun validate(s: ScriptDefinition?) {
        require(s != null && s.schema_version == 1 && s.executor != null && s.title != null && s.title.isNotBlank() && s.executor.command != null && s.executor.command.isNotBlank()) {
            "脚本名称、正文为空或数据版本不支持"
        }
        require(s.executor.kind in kinds) { "不支持的执行类型：${s.executor.kind}" }
        require(s.executor.args != null && s.executor.env != null && s.executor.requirements != null && s.parameters != null && s.presentation != null) { "脚本字段不完整" }
        require(s.executor.android_device_mode in setOf("auto", "use", "none")) { "无效的设备模式" }
        Charset.forName(s.executor.encoding)
        require(s.executor.timeout_seconds?.let { it >= 1 } != false) { "超时必须大于零" }
        val ids = hashSetOf<String>()
        for (p in s.parameters) {
            require(p.id != null && ids.add(p.id) && p.kind in parameterKinds && p.options != null) { "不支持或重复的参数：${p.id}" }
            require(p.id.isNotBlank() && p.id.codePoints().allMatch { it == '_'.code || Character.isLetterOrDigit(it) }) { "无效参数名称：${p.id}" }
            require(p.kind != "choice" || p.options.isNotEmpty()) { "选择参数没有选项：${p.id}" }
        }
        ParameterTemplates.synchronize(ParameterTemplates.templates(s), s.parameters)
    }

    @JvmStatic fun decode(element: JsonElement): ScriptDefinition {
        var o = element.asJsonObject
        if (o.has("format")) {
            require(o["format"].asString == "poptools.custom-script" && o.has("format_version") && o["format_version"].asInt == 1) { "不支持的 PopTool 分享格式版本" }
            o = o.getAsJsonObject("tool")
        }
        require(!o.has("section") || o["section"].asString in setOf("custom", "local")) { "只能导入自定义脚本" }
        checkFields(o, setOf("schema_version", "id", "revision", "origin", "section", "title", "description", "tags", "editable", "enabled", "executor", "parameters", "presentation", "interpreter"), "脚本")
        if (o.has("executor")) checkFields(o.getAsJsonObject("executor"), setOf("kind", "command", "args", "cwd", "timeout_seconds", "encoding", "env", "requirements", "android_device_mode"), "执行配置")
        if (o.has("presentation")) checkFields(o.getAsJsonObject("presentation"), setOf("icon", "order", "confirm_before_run", "output_mode"), "展示配置")
        if (o.has("parameters")) for (p in o.getAsJsonArray("parameters")) checkFields(p.asJsonObject, setOf("id", "label", "kind", "required", "default", "placeholder", "options"), "参数")
        val s = GSON.fromJson(o, ScriptDefinition::class.java)
        validate(s)
        val derived = ParameterTemplates.synchronize(ParameterTemplates.templates(s), s.parameters)
        for (p in derived) if (s.parameters.none { it.id == p.id }) s.parameters.add(p)
        s.origin = "custom"
        s.section = "custom"
        s.editable = true
        s.enabled = true
        return s
    }

    private fun checkFields(o: JsonObject, supported: Set<String>, label: String) {
        for (key in o.keySet()) require(key in supported) { "${label}包含不支持的字段：$key" }
    }

    @JvmStatic fun share(script: ScriptDefinition): String {
        val tool = GSON.toJsonTree(script).asJsonObject
        tool.remove("interpreter")
        for (p in tool.getAsJsonArray("parameters")) p.asJsonObject.remove("default")
        val executor = tool.getAsJsonObject("executor")
        executor.addProperty("command", ParameterTemplates.stripDefaults(executor["command"].asString))
        val args = JsonArray()
        executor.getAsJsonArray("args").forEach { args.add(ParameterTemplates.stripDefaults(it.asString)) }
        executor.add("args", args)
        val env = JsonObject()
        executor.getAsJsonObject("env").entrySet().forEach { (key, value) -> env.addProperty(key, ParameterTemplates.stripDefaults(value.asString)) }
        executor.add("env", env)
        if (executor.has("cwd") && !executor["cwd"].isJsonNull) executor.addProperty("cwd", ParameterTemplates.stripDefaults(executor["cwd"].asString))
        val payload = JsonObject()
        payload.addProperty("format", "poptools.custom-script")
        payload.addProperty("format_version", 1)
        payload.add("tool", tool)
        return GSON.toJson(payload)
    }
}
