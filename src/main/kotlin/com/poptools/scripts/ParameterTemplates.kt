package com.poptools.scripts

import com.poptools.scripts.ScriptDefinition.Option
import com.poptools.scripts.ScriptDefinition.Parameter
import java.math.BigDecimal
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Literal substitution, including PopTool legacy syntax. Never shell-escape values here. */
object ParameterTemplates {
    private val placeholder = Pattern.compile("\\$\\{([^{}]+)}")
    private val declaration = Pattern.compile("^[ \\t]*(?:var|Var|pVal)[ \\t]+([^\\s:=]+)[ \\t]*(?::|=)[ \\t]*\\$\\{([^{}\\r\\n]+)}[ \\t]*(?:\\r?\\n|$)", Pattern.MULTILINE)
    private data class Parsed(val id: String, val defaultValue: String?, val kind: String, val options: List<Option>, val explicitKind: Boolean = false)
    private data class Definition(val label: String, val parsed: Parsed, val declared: Boolean)

    private fun validateId(id: String) {
        require(id.isNotEmpty() && id.codePoints().allMatch { it == '_'.code || Character.isLetterOrDigit(it) }) {
            "参数名称只能包含中文、字母、数字或下划线：$id"
        }
    }

    private fun choices(value: String, explicit: Boolean = false): List<Option> {
        val parts = value.split('|')
        if ((!explicit && parts.size < 2) || parts.any { '=' !in it }) {
            require(!explicit) { "选择参数需要使用 显示名称=实际值，并用 | 分隔选项" }
            return emptyList()
        }
        val labels = hashSetOf<String>()
        return parts.map { part ->
            val i = part.indexOf('=')
            val label = part.substring(0, i).trim()
            val v = part.substring(i + 1).trim()
            require(label.isNotEmpty() && v.isNotEmpty() && labels.add(label)) { "下拉选项为空或重复：$part" }
            Option(label, v)
        }
    }

    private fun parse(content: String): Parsed {
        val colon = content.indexOf(':')
        val equals = content.indexOf('=')
        val modern = colon >= 0 && (equals < 0 || colon < equals)
        if (!modern) {
            val options = choices(content)
            if (options.isNotEmpty()) {
                validateId(options.first().label)
                return Parsed(options.first().label, options.first().value, "choice", options)
            }
        }
        val split = if (modern) colon else equals
        val name = (if (split < 0) content else content.substring(0, split)).trim()
        val value = if (split < 0) null else content.substring(split + 1)
        val names = name.split('@')
        validateId(names[0])
        val kind = if (names.size == 1) "text" else {
            require(names.size == 2) { "参数类型无效：$name" }
            when (names[1].trim()) {
                "file" -> "file"
                "dir" -> "directory"
                "text", "multiline", "integer", "number", "boolean", "choice", "radio", "secret" -> names[1].trim()
                else -> throw IllegalArgumentException("不支持的参数类型：$name")
            }
        }
        if (kind in setOf("choice", "radio")) {
            require(value != null && value.isNotBlank()) { "选择参数没有选项：${names[0]}" }
            val options = choices(value, explicit = true)
            return Parsed(names[0], options.first().value, kind, options, true)
        }
        // Only the legacy untyped syntax infers choices from the payload. Explicit text is literal.
        val options = if (modern && (names.size == 1 || kind in setOf("file", "directory"))) choices(requireNotNull(value)) else emptyList()
        if (options.isNotEmpty()) {
            require(kind == "text") { "文件或目录参数不能使用下拉选项" }
            return Parsed(names[0], options.first().value, "choice", options)
        }
        return Parsed(names[0], value, kind, emptyList(), names.size == 2)
    }

    @JvmStatic fun synchronize(templates: List<String>, existing: List<Parameter>): MutableList<Parameter> {
        val definitions = linkedMapOf<String, Definition>()
        for (text in templates) {
            val m = declaration.matcher(text)
            while (m.find()) {
                val id = parse(m.group(1))
                val p = parse(m.group(2))
                val d = Definition(p.id, p.copy(id = id.id), true)
                val old = definitions.putIfAbsent(id.id, d)
                require(old == null || old == d) { "参数设置了不同声明：${id.id}" }
            }
        }
        for (text in templates) {
            val m = placeholder.matcher(declaration.matcher(text).replaceAll(""))
            while (m.find()) {
                val p = parse(m.group(1))
                val old = definitions[p.id]
                if (old == null) {
                    definitions[p.id] = Definition(p.id, p, false)
                    continue
                }
                val previous = old.parsed
                if (previous.defaultValue == null && previous.kind == "text" && !previous.explicitKind && !old.declared) {
                    definitions[p.id] = Definition(old.label, p, false)
                    continue
                }
                if (p.defaultValue != null) {
                    if (previous.defaultValue == null) {
                        require((!previous.explicitKind && previous.kind == "text") || previous.kind == p.kind) {
                            "参数的类型或选项冲突：${p.id}"
                        }
                        definitions[p.id] = Definition(old.label, p, old.declared)
                    }
                    else require(previous.defaultValue == p.defaultValue && previous.kind == p.kind && previous.options == p.options) {
                        "参数的默认值、类型或选项冲突：${p.id}"
                    }
                } else if (!(p.kind == "text" && !p.explicitKind)) {
                    require(previous.kind == p.kind && previous.options == p.options) { "参数的类型或选项冲突：${p.id}" }
                }
                // An explicit type remains authoritative even if an equivalent legacy definition came first.
                if (p.explicitKind && !definitions.getValue(p.id).parsed.explicitKind) {
                    val current = definitions.getValue(p.id)
                    definitions[p.id] = current.copy(parsed = current.parsed.copy(explicitKind = true))
                }
            }
        }
        return definitions.map { (id, d) ->
            val source = existing.find { it.id == id }
            val p = source?.let(ScriptJson::copyParameter) ?: Parameter()
            p.id = id
            if (source == null || d.declared) p.label = d.label
            p.defaultValue = d.parsed.defaultValue ?: ""
            p.options = d.parsed.options.toMutableList()
            if (d.parsed.explicitKind || d.parsed.kind != "text" || p.kind in listOf("choice", "radio", "file", "directory")) p.kind = d.parsed.kind
            p
        }.toMutableList()
    }

    @JvmStatic fun templates(script: ScriptDefinition): List<String> = buildList {
        add(script.executor.command)
        addAll(script.executor.args)
        addAll(script.executor.env.values)
        script.executor.cwd?.let(::add)
    }

    @JvmStatic fun render(template: String, values: Map<String, String>): String {
        val m = placeholder.matcher(declaration.matcher(template).replaceAll(""))
        val out = StringBuilder()
        while (m.find()) {
            val p = parse(m.group(1))
            m.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(p.id, p.defaultValue ?: "")))
        }
        m.appendTail(out)
        return out.toString()
    }

    @JvmStatic fun stripDefaults(template: String): String {
        val m = placeholder.matcher(template)
        val result = StringBuilder()
        while (m.find()) {
            val p = parse(m.group(1))
            val type = if (p.kind == "directory") "dir" else p.kind
            val name = p.id + if (p.explicitKind) "@$type" else ""
            val replacement = if (p.options.isEmpty() && p.defaultValue != null) "\${$name}" else m.group()
            m.appendReplacement(result, Matcher.quoteReplacement(replacement))
        }
        m.appendTail(result)
        return result.toString()
    }

    @JvmStatic fun renderArguments(templates: List<String>, values: Map<String, String>, parameters: List<Parameter>): List<String> = buildList {
        for (original in templates) {
            var template = original
            if (template.startsWith('?') && ':' in template) {
                val separator = template.indexOf(':')
                val id = template.substring(1, separator)
                val value = values.getOrDefault(id, "")
                val kind = parameters.find { it.id == id }?.kind ?: "text"
                val truthy = when {
                    kind == "boolean" -> value.equals("true", ignoreCase = true) || value == "1"
                    kind in listOf("integer", "number") && value.isNotEmpty() -> BigDecimal(value).signum() != 0
                    else -> value.isNotEmpty()
                }
                if (!truthy) continue
                template = template.substring(separator + 1)
            }
            add(render(template, values))
        }
    }
}
