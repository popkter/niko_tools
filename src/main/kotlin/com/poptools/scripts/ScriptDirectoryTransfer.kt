package com.poptools.scripts

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Copies user-authored script files referenced by a PopTool directory export. */
object ScriptDirectoryTransfer {
    private val first = Pattern.compile("^\\s*(?:&\\s+)?(?:\"([^\"]+)\"|'([^']+)'|(\\S+))(.*)$", Pattern.DOTALL)
    private val fileKinds = setOf("python", "powershell", "bash", "batch")
    private val scriptExtension = Regex("(?i).+\\.(py|ps1|sh|bat|cmd)")
    private fun token(m: Matcher): String = m.group(1) ?: m.group(2) ?: m.group(3)
    private fun invocation(kind: String, path: String, tail: String) = when (kind) {
        "powershell" -> "& '" + path.replace("'", "''") + "'" + tail
        "bash" -> "'" + path.replace("'", "'\"'\"'") + "'" + tail
        else -> "\"$path\"$tail"
    }

    @JvmStatic fun writeDirectory(directory: Path, scripts: List<ScriptDefinition>) {
        val output = directory.toAbsolutePath().normalize()
        Files.createDirectories(output.resolve("tools"))
        scripts.forEachIndexed { index, original ->
            val script = ScriptJson.copy(original)
            val command = script.executor.command
            val m = first.matcher(command)
            if ('\n' !in command && script.executor.kind in fileKinds && m.matches()) {
                val token = token(m)
                if (scriptExtension.matches(token)) {
                    val file = Path.of(token)
                    if (file.isAbsolute) {
                        require(Files.isRegularFile(file)) { "导出时找不到脚本文件：$file" }
                        val tree = file.toRealPath().parent
                        val relative = Path.of("scripts", UUID.randomUUID().toString())
                        val destination = output.resolve(relative)
                        Files.createDirectories(destination)
                        Files.walk(tree).use { entries ->
                            for (entry in entries.filter { !it.toAbsolutePath().normalize().startsWith(output) }.toList()) {
                                if (Files.isSymbolicLink(entry)) continue
                                val target = destination.resolve(tree.relativize(entry))
                                if (Files.isDirectory(entry)) Files.createDirectories(target) else Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                        val portable = relative.resolve(file.fileName).toString().replace('\\', '/')
                        script.executor.command = invocation(script.executor.kind, portable, m.group(4))
                    }
                }
            }
            val name = "${index + 1}-${script.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json"
            Files.writeString(output.resolve("tools").resolve(name), ScriptJson.GSON.toJson(script), StandardCharsets.UTF_8)
        }
    }

    @JvmStatic fun prepare(original: ScriptDefinition, source: Path, assets: Path): ScriptDefinition {
        val script = ScriptJson.copy(original)
        val command = script.executor.command
        if ('\n' in command || script.executor.kind !in fileKinds) return script
        val m = first.matcher(command)
        if (!m.matches()) return script
        val token = token(m)
        val normalized = token.replace('\\', '/')
        if (!scriptExtension.matches(normalized)) return script
        val relative = Path.of(token)
        if (relative.isAbsolute) return script
        val sourceRoot = source.toRealPath()
        val file = sourceRoot.resolve(relative).normalize()
        require(file.startsWith(sourceRoot)) { "脚本文件引用超出导入目录：$token" }
        require(Files.isRegularFile(file)) { "导入目录缺少脚本文件：$token" }
        require(file.toRealPath().startsWith(sourceRoot)) { "脚本文件链接指向导入目录外部：$token" }
        val tree = if (normalized.startsWith("scripts/")) sourceRoot.resolve("scripts") else file.parent
        val destination = assets.resolve(UUID.randomUUID().toString())
        Files.createDirectories(destination)
        Files.walk(tree).use { entries ->
            for (entry in entries.toList()) {
                if (Files.isSymbolicLink(entry)) continue
                val target = destination.resolve(tree.relativize(entry))
                if (Files.isDirectory(entry)) Files.createDirectories(target) else Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val installed = destination.resolve(tree.relativize(file)).toString().replace('\\', '/')
        script.executor.command = invocation(script.executor.kind, installed, m.group(4))
        return script
    }
}
