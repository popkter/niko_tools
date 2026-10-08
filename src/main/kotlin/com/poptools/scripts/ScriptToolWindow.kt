package com.poptools.scripts

import com.google.gson.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.*
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import javax.swing.table.AbstractTableModel

class ScriptToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(ScriptPanel(project).component, "", false))
    }

    class ScriptPanel(private val project: Project) {
        private val scripts = arrayListOf<ScriptDefinition>()
        private val model = object : AbstractTableModel() {
            private val columns = arrayOf("名称", "类型", "说明")
            override fun getRowCount() = scripts.size
            override fun getColumnCount() = columns.size
            override fun getColumnName(column: Int) = columns[column]
            override fun getValueAt(row: Int, column: Int): Any = scripts[row].let {
                when (column) { 0 -> it; 1 -> typeLabel(it.executor.kind); else -> it.description }
            }
            override fun isCellEditable(row: Int, column: Int) = false
        }
        @JvmField val scriptTable = JBTable(model)
        @JvmField val globalActions = DefaultActionGroup()
        @JvmField val toolbar: ActionToolbar
        @JvmField val component: DialogPanel
        private lateinit var details: JLabel

        init {
            scriptTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            scriptTable.rowSelectionAllowed = true; scriptTable.columnSelectionAllowed = false
            scriptTable.setShowGrid(false); scriptTable.intercellSpacing = Dimension(0, 0)
            scriptTable.rowHeight = JBUI.scale(28); scriptTable.fillsViewportHeight = true
            scriptTable.tableHeader.reorderingAllowed = false
            scriptTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(240)
            scriptTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(95)
            scriptTable.columnModel.getColumn(1).maxWidth = JBUI.scale(120)
            scriptTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(240)
            scriptTable.setDefaultRenderer(Any::class.java, object : ColoredTableCellRenderer() {
                override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, focused: Boolean, row: Int, column: Int) {
                    border = JBUI.Borders.empty(0, 8)
                    // Keep the icon region on the same background as the rest of the selected cell.
                    setFocusBorderAroundIcon(true)
                    if (selected) { isOpaque = true; background = table.selectionBackground }
                    if (value is ScriptDefinition) {
                        icon = ScriptIcons.forKind(value.executor.kind); append(value.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        toolTipText = value.description.ifBlank { value.title }
                    } else {
                        append(value?.toString() ?: "", if (selected) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        toolTipText = value?.toString() ?: ""
                    }
                }
            })
            scriptTable.emptyText.text = "暂无脚本，点击工具栏“新建脚本”或“导入”"
            globalActions.add(action("新建脚本", "新建自定义脚本", AllIcons.Actions.AddFile) { edit(ScriptDefinition(), false) })
            globalActions.add(action("批量操作", "批量导入或导出脚本目录", AllIcons.Actions.SyncPanels, ::directoryTransfer))
            globalActions.add(action("导入", "从剪贴板导入脚本", AllIcons.Actions.Download, ::importClipboard))
            globalActions.add(DefaultActionGroup("排序", true).apply {
                templatePresentation.icon = AllIcons.ObjectBrowser.Sorted
                templatePresentation.description = "设置脚本列表排序方式"
                ScriptLibrary.SortOrder.values().forEach { order ->
                    add(object : ToggleAction("按${order.label}") {
                        override fun isSelected(e: AnActionEvent) = ScriptLibrary.getInstance().sortOrder() == order
                        override fun setSelected(e: AnActionEvent, state: Boolean) {
                            if (state) ScriptLibrary.getInstance().setSortOrder(order)
                        }
                        override fun getActionUpdateThread() = ActionUpdateThread.EDT
                    })
                }
            })
            globalActions.add(action("环境路径", "配置解释器和工具路径", AllIcons.General.Settings) { ShowSettingsUtil.getInstance().showSettingsDialog(project, EnvironmentSettings::class.java) })
            toolbar = ActionManager.getInstance().createActionToolbar("NikoTools.ScriptToolbar", globalActions, true)
            toolbar.targetComponent = scriptTable
            component = panel {
                row { cell(toolbar.component).align(AlignX.FILL).resizableColumn() }
                row { scrollCell(scriptTable).align(Align.FILL).resizableColumn() }.resizableRow()
                row { details = label("双击脚本执行；右键打开脚本操作菜单。").component }
            }
            component.putClientProperty("poptool.scriptPanel", this)
            ScriptLibrary.getInstance().listen(::refresh, project); refresh()
            scriptTable.selectionModel.addListSelectionListener {
                val s = selected()
                details.text = s?.description?.ifBlank { typeLabel(s.executor.kind) } ?: "双击脚本执行；右键打开脚本操作菜单。"
            }
            scriptTable.addMouseListener(object : MouseAdapter() {
                private fun popup(event: MouseEvent) {
                    if (!event.isPopupTrigger) return
                    val script = scriptAt(event.point) ?: return
                    createScriptMenu(script).component.show(scriptTable, event.x, event.y)
                }
                override fun mousePressed(e: MouseEvent) = popup(e)
                override fun mouseReleased(e: MouseEvent) = popup(e)
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) scriptAt(e.point)?.let { ScriptRunner.run(project, it, emptyMap()) }
                }
            })
            scriptTable.inputMap.put(KeyStroke.getKeyStroke("shift F10"), "nikoTools.popup")
            scriptTable.inputMap.put(KeyStroke.getKeyStroke("CONTEXT_MENU"), "nikoTools.popup")
            scriptTable.actionMap.put("nikoTools.popup", object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    val script = selected() ?: return
                    val row = scriptTable.getCellRect(scriptTable.selectedRow, 0, true)
                    createScriptMenu(script).component.show(scriptTable, row.x + JBUI.scale(24), row.y + row.height)
                }
            })
            scriptTable.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "nikoTools.delete")
            // The Mac keyboard's Delete key generates Backspace without Fn.
            scriptTable.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "nikoTools.delete")
            scriptTable.actionMap.put("nikoTools.delete", object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    if (scriptTable.selectedRowCount == 1) selected()?.let(::deleteScript)
                }
            })
        }
        private fun typeLabel(kind: String) = when (kind) {
            "powershell" -> "PowerShell"; "python" -> "Python"; "bash" -> "Bash"; "batch" -> "BAT"; else -> "外部进程"
        }
        fun scriptAt(point: Point): ScriptDefinition? {
            val index = scriptTable.rowAtPoint(point)
            if (index < 0 || point.x < 0 || point.x >= scriptTable.width) return null
            scriptTable.requestFocusInWindow(); scriptTable.setRowSelectionInterval(index, index)
            return scripts[scriptTable.convertRowIndexToModel(index)]
        }
        fun createScriptMenu(script: ScriptDefinition): ActionPopupMenu {
            val group = DefaultActionGroup()
            group.add(action("执行脚本", "执行所选脚本", AllIcons.Actions.Execute) { ScriptRunner.run(project, script, emptyMap()) })
            group.add(action("编辑脚本", "编辑所选脚本", AllIcons.Actions.Edit) { edit(script, false) })
            group.add(action("分享脚本", "复制分享内容到剪贴板", AllIcons.Actions.Share) { shareClipboard(script) })
            group.addSeparator()
            group.add(action("删除脚本", "删除所选脚本", AllIcons.Actions.GC) { deleteScript(script) })
            return ActionManager.getInstance().createActionPopupMenu("NikoTools.ScriptPopup", group).also { it.setTargetComponent(scriptTable) }
        }
        private fun deleteScript(script: ScriptDefinition) {
            if (project.isDisposed) return
            if (Messages.showYesNoDialog(project, "删除“${script.title}”？", "确认删除", Messages.getQuestionIcon()) == Messages.YES) ScriptLibrary.getInstance().delete(script.id, false)
        }
        private fun action(title: String, description: String, icon: Icon, run: () -> Unit): AnAction = object : DumbAwareAction(title, description, icon) {
            override fun actionPerformed(e: AnActionEvent) = run()
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = !project.isDisposed }
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }
        private fun selected(): ScriptDefinition? {
            val row = scriptTable.selectedRow
            return if (row < 0 || row >= scripts.size) null else scripts[scriptTable.convertRowIndexToModel(row)]
        }
        private fun edit(s: ScriptDefinition, template: Boolean) = ScriptEditor(project, s, template).show()
        private fun refresh() {
            val sid = selected()?.id
            scriptTable.clearSelection(); scripts.clear(); scripts.addAll(ScriptLibrary.getInstance().sortedScripts()); model.fireTableDataChanged()
            scripts.forEachIndexed { i, script -> if (script.id == sid) scriptTable.setRowSelectionInterval(i, i) }
        }
        private fun importClipboard() {
            try {
                val text = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String ?: throw IllegalArgumentException("剪贴板没有脚本 JSON")
                importJson(text)
            } catch (e: Exception) { Messages.showErrorDialog(project, e.message ?: e.toString(), "导入失败") }
        }
        private fun importJson(text: String, sourceDirectory: Path? = null) {
            val root = JsonParser.parseString(text)
            val entries = when {
                root.isJsonArray -> root.asJsonArray.toList()
                root.isJsonObject && root.asJsonObject.has("scripts") -> root.asJsonObject.getAsJsonArray("scripts").toList()
                else -> listOf(root)
            }
            val template = false
            var success = 0; var skipped = 0
            val errors = arrayListOf<String>()
            entries.forEachIndexed { index, entry ->
                try {
                    var s = ScriptJson.decode(entry)
                    val conflict = ScriptLibrary.getInstance().list(template).find { it.id == s.id || it.title == s.title }
                    if (conflict != null) {
                        when (Messages.showDialog(project, "“${s.title}”已存在，请选择处理方式。", "导入冲突", arrayOf("覆盖", "另存", "跳过"), 2, Messages.getQuestionIcon())) {
                            0 -> s.id = conflict.id
                            1 -> { s.id = ScriptDefinition().id; s.title = uniqueTitle(s.title, template) }
                            else -> { skipped++; return@forEachIndexed }
                        }
                    }
                    if (sourceDirectory != null) s = ScriptDirectoryTransfer.prepare(s, sourceDirectory, Path.of(PathManager.getConfigPath(), "poptool-assets"))
                    ScriptLibrary.getInstance().save(s, template); success++
                } catch (e: Exception) { errors.add("第 ${index + 1} 项：${e.message ?: "数据格式错误"}") }
            }
            Messages.showInfoMessage(project, "成功 $success，跳过 $skipped，失败 ${errors.size}\n${errors.joinToString("\n")}", "导入结果")
        }
        private fun uniqueTitle(title: String, template: Boolean): String {
            val used = ScriptLibrary.getInstance().list(template).map { it.title }.toSet()
            var suffix = 2
            var candidate = "$title ($suffix)"
            while (candidate in used) { suffix++; candidate = "$title ($suffix)" }
            return candidate
        }
        private fun shareClipboard(s: ScriptDefinition) {
            try {
                CopyPasteManager.getInstance().setContents(StringSelection(ScriptJson.share(s)))
                Messages.showInfoMessage(project, "脚本已复制到剪贴板，默认值已去除。", "分享脚本")
            } catch (e: Exception) { Messages.showErrorDialog(project, e.toString(), "导出失败") }
        }
        private fun directoryTransfer() {
            val action = Messages.showDialog(project, "批量导入或导出脚本目录。", "批量操作", arrayOf("导入目录", "导出目录", "取消"), 2, Messages.getQuestionIcon())
            if (action != 0 && action != 1) return
            val chosen = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, projectRoot(project)) ?: return
            val directory = chosen.toNioPath()
            try {
                if (action == 0) {
                    val data = JsonArray()
                    val invalid = arrayListOf<String>()
                    val tools = directory.resolve("tools").takeIf { Files.isDirectory(it) } ?: directory
                    Files.walk(tools).use { files ->
                        for (file in files.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }.toList()) {
                            try {
                                val json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                                if (json.isJsonArray) json.asJsonArray.forEach(data::add) else data.add(json)
                            } catch (e: Exception) { invalid.add("${file.fileName}：${e.message}") }
                        }
                    }
                    require(!data.isEmpty || invalid.isNotEmpty()) { "目录中没有脚本 JSON" }
                    val backup = Path.of(PathManager.getConfigPath(), "poptool-backups", System.currentTimeMillis().toString())
                    ScriptDirectoryTransfer.writeDirectory(backup, ScriptLibrary.getInstance().list(false))
                    importJson(data.toString(), directory)
                    if (invalid.isNotEmpty()) Messages.showErrorDialog(project, invalid.joinToString("\n"), "无法读取的脚本文件")
                } else {
                    val destination = directory.resolve("poptool-scripts-${System.currentTimeMillis()}")
                    ScriptDirectoryTransfer.writeDirectory(destination, ScriptLibrary.getInstance().list(false))
                    Messages.showInfoMessage(project, "已导出到：$destination", "导出脚本目录")
                }
            } catch (e: Exception) { Messages.showErrorDialog(project, e.message ?: e.toString(), "批量操作失败") }
        }
    }
}
