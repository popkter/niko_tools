package com.poptools.scripts

import java.util.Objects
import java.util.UUID

/** Matches PopTool v1 tool JSON; plugin-specific fields are additive. */
class ScriptDefinition {
    @JvmField var schema_version = 1
    @JvmField var id = "custom." + UUID.randomUUID().toString().replace("-", "")
    @JvmField var revision = 1
    @JvmField var origin = "custom"
    @JvmField var section = "custom"
    @JvmField var title = "新建脚本"
    @JvmField var description = ""
    @JvmField var editable = true
    @JvmField var enabled = true
    @JvmField var tags: MutableList<String> = arrayListOf()
    @JvmField var executor = Executor()
    @JvmField var parameters: MutableList<Parameter> = arrayListOf()
    @JvmField var presentation = Presentation()
    @JvmField var interpreter = ""

    class Executor {
        @JvmField var kind = "powershell"
        @JvmField var command = ""
        @JvmField var cwd: String? = null
        @JvmField var encoding = "utf-8"
        @JvmField var args: MutableList<String> = arrayListOf()
        @JvmField var requirements: MutableList<String> = arrayListOf()
        @JvmField var env: MutableMap<String, String> = linkedMapOf()
        @JvmField var timeout_seconds: Int? = null
        // Legacy PopTool field: retained for JSON compatibility, no longer controls execution.
        @JvmField var android_device_mode = "auto"
    }

    class Parameter {
        @JvmField var id = ""
        @JvmField var label: String? = null
        @JvmField var kind = "text"
        @JvmField var placeholder = ""
        @JvmField var defaultValue: Any? = ""
        @JvmField var required = true
        @JvmField var options: MutableList<Option> = arrayListOf()
    }

    class Option @JvmOverloads constructor(@JvmField var label: String = "", @JvmField var value: String = "") {
        override fun toString() = label
        override fun equals(other: Any?) = other is Option && label == other.label && value == other.value
        override fun hashCode() = Objects.hash(label, value)
    }

    class Presentation {
        @JvmField var icon = "terminal"
        @JvmField var output_mode = "console"
        @JvmField var order = 100
        @JvmField var confirm_before_run = false
    }

    override fun toString() = title
}
