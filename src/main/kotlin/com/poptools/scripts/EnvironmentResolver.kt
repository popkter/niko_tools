package com.poptools.scripts

import java.io.File
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.Locale

class EnvironmentResolver(private val paths: Map<String, String>, private val environment: Map<String, String>) {
    fun resolve(name: String, override: String?): Path {
        val configured = override?.takeUnless { it.isBlank() } ?: paths.getOrDefault(name, "")
        if (configured.isNotBlank()) {
            val path = Path.of(configured)
            require(path.isAbsolute && Files.isRegularFile(path)) { "$name 配置路径不存在或不是绝对文件路径：$configured。请在 NikoTools 设置中修改。" }
            require(name != "bash" || !isWslBash(path)) { "首版不支持 WSL Bash，请指定 Windows 本机 Bash（例如 Git Bash）路径。" }
            return path
        }
        val names = when (name) { "powershell" -> listOf("pwsh", "powershell"); "python" -> listOf("python", "python3"); else -> listOf(name) }
        val pathValue = environment.entries.find { it.key.equals("PATH", ignoreCase = true) }?.value ?: ""
        for (candidate in names) {
            val explicit = Path.of(candidate)
            if (explicit.isAbsolute && Files.isRegularFile(explicit)) return explicit
            if (explicit.isAbsolute) continue
            for (dir in pathValue.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                for (extension in listOf("", ".exe", ".cmd", ".bat")) {
                    try {
                        val found = Path.of(dir.replace("\"", ""), candidate + extension)
                        if (Files.isRegularFile(found) && !(name == "bash" && isWslBash(found))) return found.toAbsolutePath()
                    } catch (_: InvalidPathException) { }
                }
            }
        }
        throw IllegalArgumentException("缺少运行环境：$name。系统环境未找到该工具，请安装并加入 PATH，或在 NikoTools 设置中指定路径。")
    }

    private fun isWslBash(path: Path): Boolean {
        val normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/').lowercase(Locale.ROOT)
        return normalized.endsWith("/windows/system32/bash.exe") || normalized.endsWith("/windows/sysnative/bash.exe")
    }

    fun executionEnvironment(requirements: Collection<String>): MutableMap<String, String> {
        val env = LinkedHashMap(environment)
        val dirs = arrayListOf<String>()
        for (required in requirements) {
            val file = resolve(required, "")
            dirs.add(file.parent.toString())
            env["POPTOOLS_" + required.uppercase(Locale.ROOT)] = file.toString()
        }
        for ((name, value) in paths) {
            if (value.isBlank()) continue
            val file = Path.of(value)
            if (Files.isRegularFile(file)) {
                dirs.add(file.parent.toString())
                env["POPTOOLS_" + name.uppercase(Locale.ROOT)] = file.toString()
            }
        }
        val key = env.keys.find { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        env[key] = dirs.distinct().joinToString(File.pathSeparator) + (if (dirs.isEmpty()) "" else File.pathSeparator) + env.getOrDefault(key, "")
        return env
    }
}
