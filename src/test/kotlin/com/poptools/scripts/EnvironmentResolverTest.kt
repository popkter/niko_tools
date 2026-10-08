package com.poptools.scripts

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class EnvironmentResolverTest {
    private fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory("niko-git-bash-test")
        try { test(directory) } finally {
            Files.walk(directory).use { files -> files.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }

    private fun executable(root: Path, relative: String): Path {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        return Files.writeString(file, "")
    }

    @Test fun windowsPrefersGitOnPathOverOtherBashAndCommonInstallation() = withDirectory { root ->
        val other = executable(root, "other/bash.exe")
        val git = executable(root, "portable/cmd/git.exe")
        val bash = executable(root, "portable/bin/bash.exe")
        executable(root, "program-files/Git/bin/bash.exe")
        val environment = mapOf("Path" to "${other.parent};\"${git.parent}\"", "ProgramFiles" to root.resolve("program-files").toString())
        assertEquals(bash, EnvironmentResolver(emptyMap(), environment, true).resolve("bash", ""))
        assertEquals(other, EnvironmentResolver(mapOf("bash" to other.toString()), environment, true).resolve("bash", ""))
        assertEquals(other, EnvironmentResolver(emptyMap(), environment, true).resolve("bash", other.toString()))
        val missing = root.resolve("missing.exe").toString()
        assertThrows(IllegalArgumentException::class.java) { EnvironmentResolver(mapOf("bash" to missing), environment, true).resolve("bash", "") }
    }

    @Test fun windowsFindsGitOutsidePathAndFromUsrBin() = withDirectory { root ->
        val bash = executable(root, "program-files/Git/bin/bash.exe")
        val environment = mapOf("PATH" to "", "programfiles" to root.resolve("program-files").toString())
        assertEquals(bash, EnvironmentResolver(emptyMap(), environment, true).resolve("bash", ""))
        val userBash = executable(root, "local/Programs/Git/usr/bin/bash.exe")
        assertEquals(userBash, EnvironmentResolver(emptyMap(), mapOf("LOCALAPPDATA" to root.resolve("local").toString()), true).resolve("bash", ""))
        executable(root, "portable/mingw64/bin/git.exe")
        val portable = executable(root, "portable/usr/bin/bash.exe")
        assertEquals(portable, EnvironmentResolver(emptyMap(), mapOf("PATH" to portable.parent.toString()), true).resolve("bash", ""))
    }

    @Test fun windowsDoesNotFallBackToUnrelatedBashAndUnixStillUsesPath() = withDirectory { root ->
        val bash = executable(root, "other/bash.exe")
        val environment = mapOf("PATH" to bash.parent.toString(), "ProgramFiles" to root.resolve("none").toString(), "ProgramFiles(x86)" to root.resolve("none-x86").toString())
        val error = assertThrows(IllegalArgumentException::class.java) { EnvironmentResolver(emptyMap(), environment, true).resolve("bash", "") }
        assertTrue(error.message!!.contains("Git Bash"))
        assertEquals(bash, EnvironmentResolver(emptyMap(), environment, false).resolve("bash", ""))
    }
}
