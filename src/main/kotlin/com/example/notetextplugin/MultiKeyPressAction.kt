package com.example.notetextplugin

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class MultiKeyPressAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = getActiveEditor(e) ?: return

        // 这个动作现在只是作为一个入口点，实际的按键检测在KeyTypedListener中处理
        // 此处保留作为菜单项的入口
        val service = project.getService(FileStateService::class.java)
        val selectedFilePath = service.getFilePath()

        if (selectedFilePath.isEmpty()) {
            com.intellij.openapi.ui.Messages.showMessageDialog(
                project,
                "没有选择任何文件，请先在工具窗口中选择一个文件。",
                "无文件可打开",
                com.intellij.openapi.ui.Messages.getWarningIcon()
            )
            return
        }

        val file = java.io.File(selectedFilePath)
        if (!file.exists()) {
            com.intellij.openapi.ui.Messages.showMessageDialog(
                project,
                "文件不存在: $selectedFilePath",
                "文件错误",
                com.intellij.openapi.ui.Messages.getErrorIcon()
            )
            return
        }

        if (file.isDirectory) {
            com.intellij.openapi.ui.Messages.showMessageDialog(
                project,
                "不能打开目录: $selectedFilePath",
                "无效路径",
                com.intellij.openapi.ui.Messages.getWarningIcon()
            )
            return
        }

        try {
            val content = String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(selectedFilePath)))
            val fileName = file.name
            
            // 显示弹窗
            FileContentPopup.show(editor, content, fileName)
            
        } catch (ex: Exception) {
            com.intellij.openapi.ui.Messages.showMessageDialog(
                project,
                "读取文件时出错: \${ex.message}",
                "读取错误",
                com.intellij.openapi.ui.Messages.getErrorIcon()
            )
        }
    }


    override fun update(e: AnActionEvent) {
        // 更新动作状态
        val editor = getActiveEditor(e)
        e.presentation.isEnabledAndVisible = editor != null
    }

    private fun getActiveEditor(e: AnActionEvent): Editor? {
        val project = e.project ?: return null

        // 首先尝试从动作事件中获取编辑器
        var editor = e.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            return editor
        }

        // 如果从事件中获取不到，则尝试从文件管理器中获取
        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedEditor = fileEditorManager.selectedTextEditor
        return selectedEditor
    }
}