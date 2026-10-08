package com.poptools.scripts

import com.google.gson.JsonParser
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.*
import org.junit.Test

/** Guard formats read from older Java-based plugin installations. */
class KotlinMigrationTest {
    @Test fun omittedFieldsKeepOriginalDefaults() {
        val script = ScriptJson.decode(JsonParser.parseString("""{"title":"Existing script","executor":{"command":"echo hello"}}"""))
        assertEquals(1, script.schema_version)
        assertEquals("powershell", script.executor.kind)
        assertEquals("utf-8", script.executor.encoding)
        assertEquals("auto", script.executor.android_device_mode)
        assertNull(script.executor.cwd)
        assertNull(script.executor.timeout_seconds)
        assertEquals(100, script.presentation.order)
        assertFalse(script.presentation.confirm_before_run)
        assertTrue(script.parameters.isEmpty())
        assertTrue(script.executor.args.isEmpty())
    }

    @Test fun metadataAndLegacyOptionFormatsRoundTrip() {
        val script = ScriptJson.decode(JsonParser.parseString("""{
            "id":"custom.saved", "revision":7, "title":"Saved script",
            "executor":{"command":"echo ${'$'}{mode}","args":["","中文 空格"],"env":{"TEST":"value"},"cwd":"/tmp","timeout_seconds":30},
            "parameters":[{"id":"mode","label":"模式","kind":"choice","default":12,"required":false,"options":["one",{"label":"Two","value":"2"}]}]
        }"""))
        val copied = ScriptJson.copy(script)
        assertEquals("custom.saved", copied.id)
        assertEquals(7, copied.revision)
        assertEquals(12L, copied.parameters.single().defaultValue)
        assertEquals("one", copied.parameters.single().options.first().value)
        assertEquals("2", copied.parameters.single().options.last().value)
        assertFalse(copied.parameters.single().required)
        assertEquals(listOf("", "中文 空格"), copied.executor.args)
        val json = ScriptJson.GSON.toJsonTree(copied).asJsonObject
        val parameter = json.getAsJsonArray("parameters")[0].asJsonObject
        assertTrue(parameter.has("default"))
        assertFalse(parameter.has("defaultValue"))
        assertFalse(json.has("Companion"))
    }

    @Test fun explicitNullRequiredFieldsAreRejected() {
        val cases = listOf(
            """{"title":null,"executor":{"command":"echo"}}""",
            """{"title":"Bad","executor":{"command":null}}""",
            """{"title":"Bad","executor":{"command":"echo","args":null}}""",
            """{"title":"Bad","executor":{"command":"echo"},"parameters":null}"""
        )
        for (json in cases) {
            val script = ScriptJson.GSON.fromJson(json, ScriptDefinition::class.java)
            assertThrows(IllegalArgumentException::class.java) { ScriptJson.validate(script) }
        }
    }

    @Test fun persistentLibraryStateReadsLegacyXmlAndReturnsDetachedSnapshots() {
        val legacy = org.jdom.Element("state").apply {
            addContent(org.jdom.Element("option").setAttribute("name", "scripts").addContent(
                org.jdom.Element("list").addContent(org.jdom.Element("option").setAttribute("value", """{"id":"custom.saved","title":"Saved script","executor":{"command":"echo saved"}}"""))
            ))
            addContent(org.jdom.Element("option").setAttribute("name", "paths").addContent(
                org.jdom.Element("map").addContent(org.jdom.Element("entry").setAttribute("key", "python").setAttribute("value", "/usr/bin/python3"))
            ))
        }
        val state = XmlSerializer.deserialize(legacy, ScriptLibrary.State::class.java)
        val library = ScriptLibrary()
        library.loadState(state)
        assertEquals("custom.saved", library.list(false).single().id)
        assertEquals("/usr/bin/python3", library.paths()["python"])
        val snapshot = library.state
        snapshot.scripts.clear()
        snapshot.paths.clear()
        assertEquals(1, library.list(false).size)
        assertEquals(1, library.paths().size)
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(library.state), ScriptLibrary.State::class.java)
        assertEquals(state.scripts, restored.scripts)
        assertEquals(state.paths, restored.paths)
    }

    @Test fun registeredExtensionClassesHavePublicNoArgConstructors() {
        val classes = listOf(ScriptLibrary::class.java, EnvironmentSettings::class.java, ScriptToolWindow::class.java,
            ManageScriptsAction::class.java, NewFromTerminalAction::class.java, TerminalIntegration::class.java, AndroidPluginDeviceProvider::class.java)
        for (type in classes) assertNotNull(type.getConstructor())
    }
}
