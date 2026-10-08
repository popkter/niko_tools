package com.poptools.scripts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

@State(name = "PopToolScriptLibrary", storages = [Storage("poptool-scripts.xml")])
class ScriptLibrary : PersistentStateComponent<ScriptLibrary.State> {
    class State {
        @JvmField var scripts: MutableList<String> = arrayListOf()
        @JvmField var paths: MutableMap<String, String> = linkedMapOf()
    }

    private var storedState = State()
    private val listeners = CopyOnWriteArrayList<Runnable>()

    companion object {
        @JvmStatic fun getInstance(): ScriptLibrary = ApplicationManager.getApplication().getService(ScriptLibrary::class.java)
    }

    override fun getState() = State().also {
        it.scripts = ArrayList(storedState.scripts)
        it.paths = LinkedHashMap(storedState.paths)
    }
    override fun loadState(state: State) { storedState = state }
    @Suppress("UNUSED_PARAMETER")
    fun list(ignored: Boolean): List<ScriptDefinition> = storedState.scripts.map { ScriptJson.GSON.fromJson(it, ScriptDefinition::class.java) }
    @Suppress("UNUSED_PARAMETER")
    fun save(script: ScriptDefinition, template: Boolean) {
        ScriptJson.validate(script)
        storedState.scripts.removeIf { ScriptJson.GSON.fromJson(it, ScriptDefinition::class.java).id == script.id }
        storedState.scripts.add(ScriptJson.GSON.toJson(script))
        changed()
    }
    @Suppress("UNUSED_PARAMETER")
    fun delete(id: String, template: Boolean) {
        storedState.scripts.removeIf { ScriptJson.GSON.fromJson(it, ScriptDefinition::class.java).id == id }
        changed()
    }
    fun paths(): Map<String, String> = LinkedHashMap(storedState.paths)
    fun setPaths(paths: Map<String, String>) { storedState.paths = LinkedHashMap(paths) }
    fun setParameterDefault(scriptId: String, parameterId: String, value: String) {
        val script = list(false).find { it.id == scriptId } ?: throw IllegalArgumentException("请先保存脚本")
        val parameter = script.parameters.find { it.id == parameterId } ?: throw IllegalArgumentException("参数不存在")
        parameter.defaultValue = value
        script.revision++
        save(script, false)
    }
    fun listen(listener: Runnable, owner: Disposable) {
        listeners.add(listener)
        Disposer.register(owner, Disposable { listeners.remove(listener) })
    }
    private fun changed() = listeners.forEach { it.run() }
}
