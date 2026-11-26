package com.example.viewtxt

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager

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
        FileContentPresenter.show(project, editor)
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
