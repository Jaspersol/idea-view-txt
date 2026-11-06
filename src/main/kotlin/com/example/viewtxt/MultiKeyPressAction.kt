package com.example.viewtxt

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import java.io.File

/**
 * Tools 菜单入口：按工具窗口里保存的路径读取文件内容并弹出预览。
 *
 * 连击触发是在 [KeyTypedListener] 中检测的，这里的 Action 只提供菜单项与可用状态，
 * 因此菜单点击和连击走的是同一套路径读取与校验逻辑。
 */
class MultiKeyPressAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = getActiveEditor(e) ?: return
        val selectedFilePath = project.getService(FileStateService::class.java).lastSelectedFilePath

        if (selectedFilePath.isEmpty()) {
            Messages.showMessageDialog(
                project,
                "没有选择任何文件，请先在工具窗口中选择一个文件。",
                "无文件可打开",
                Messages.getWarningIcon(),
            )
            return
        }

        val file = File(selectedFilePath)
        if (!file.exists()) {
            Messages.showMessageDialog(
                project,
                "文件不存在: $selectedFilePath",
                "文件错误",
                Messages.getErrorIcon(),
            )
            return
        }

        if (file.isDirectory) {
            Messages.showMessageDialog(
                project,
                "不能打开目录: $selectedFilePath",
                "无效路径",
                Messages.getWarningIcon(),
            )
            return
        }

        // 读取与解码都在后台线程，大文件不会卡住 EDT；弹窗创建回到 EDT 执行
        fun showReadError(ex: Throwable) {
            Messages.showMessageDialog(
                project,
                "读取文件时出错: ${ex.message}",
                "读取错误",
                Messages.getErrorIcon(),
            )
        }

        FileContentLoader.readTextAsync(
            file,
            onSuccess = { content ->
                try {
                    FileContentPopup.show(editor, content, file.name)
                } catch (ex: Exception) {
                    showReadError(ex)
                }
            },
            onError = { ex -> showReadError(ex) },
        )
    }

    override fun update(e: AnActionEvent) {
        val editor = getActiveEditor(e)
        e.presentation.isEnabledAndVisible = editor != null
    }

    /**
     * 优先取动作事件里的编辑器，取不到（例如从菜单触发的场景）再退回当前选中的文本编辑器。
     */
    private fun getActiveEditor(e: AnActionEvent): Editor? {
        val project = e.project ?: return null

        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            return editor
        }

        val fileEditorManager = FileEditorManager.getInstance(project)
        return fileEditorManager.selectedTextEditor
    }
}
