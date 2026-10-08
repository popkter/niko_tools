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
    enum class SortOrder(val label: String) {
        USAGE("使用频率"), ADDED("添加顺序"), NAME("名称")
    }

    class State {
        @JvmField var scripts: MutableList<String> = arrayListOf()
        @JvmField var paths: MutableMap<String, String> = linkedMapOf()
        @JvmField var usageCounts: MutableMap<String, Long> = linkedMapOf()
        @JvmField var sortOrder: String = SortOrder.ADDED.name
    }

    private var storedState = State()
    private val listeners = CopyOnWriteArrayList<Runnable>()

    companion object {
        @JvmStatic fun getInstance(): ScriptLibrary = ApplicationManager.getApplication().getService(ScriptLibrary::class.java)
    }

    override fun getState() = State().also {
        it.scripts = ArrayList(storedState.scripts)
        it.paths = LinkedHashMap(storedState.paths)
        it.usageCounts = LinkedHashMap(storedState.usageCounts)
        it.sortOrder = storedState.sortOrder
    }
    override fun loadState(state: State) { storedState = state }
    @Suppress("UNUSED_PARAMETER")
    fun list(ignored: Boolean): List<ScriptDefinition> = storedState.scripts.map { ScriptJson.GSON.fromJson(it, ScriptDefinition::class.java) }
    @Suppress("UNUSED_PARAMETER")
    fun save(script: ScriptDefinition, template: Boolean) {
        ScriptJson.validate(script)
        val index = storedState.scripts.indexOfFirst { ScriptJson.GSON.fromJson(it, ScriptDefinition::class.java).id == script.id }
        val json = ScriptJson.GSON.toJson(script)
        if (index < 0) storedState.scripts.add(json) else storedState.scripts[index] = json
        changed()
    }
    @Suppress("UNUSED_PARAMETER")
    fun delete(id: String, template: Boolean) {
        storedState.scripts.removeIf { ScriptJson.GSON.fromJson(it, ScriptDefinition::class.java).id == id }
        storedState.usageCounts.remove(id)
        changed()
    }
    fun sortOrder(): SortOrder = SortOrder.values().find { it.name == storedState.sortOrder } ?: SortOrder.ADDED
    fun setSortOrder(order: SortOrder) {
        storedState.sortOrder = order.name
        changed()
    }
    fun sortedScripts(): List<ScriptDefinition> {
        val scripts = list(false)
        // Stable sorting preserves addition order for matching names or usage counts.
        return when (sortOrder()) {
            SortOrder.ADDED -> scripts
            SortOrder.USAGE -> scripts.sortedByDescending { storedState.usageCounts[it.id] ?: 0L }
            SortOrder.NAME -> scripts.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }
    fun recordUsage(id: String) {
        if (list(false).none { it.id == id }) return
        storedState.usageCounts[id] = (storedState.usageCounts[id] ?: 0L) + 1L
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
