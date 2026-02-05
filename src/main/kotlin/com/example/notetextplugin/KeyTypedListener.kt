package com.example.notetextplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

class KeyTypedListener : EditorFactoryListener {
    companion object {
        private const val PRESS_THRESHOLD = 3
        private const val RESET_TIMEOUT_MS = 2000 // 2秒超时重置计数
    }

    // 使用WeakHashMap来存储每个编辑器的按键计数，避免内存泄漏
    private val editorPressCounts = java.util.WeakHashMap<Editor, Int>()
    private val editorLastPressTimes = java.util.WeakHashMap<Editor, Long>()

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        val keyAdapter = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                // 获取当前设置的快捷键
                val service = project.getService(FileStateService::class.java)
                val targetKey = service.getPopupShortcut()
                
                if (e.keyCode == targetKey) {
                    val currentTime = System.currentTimeMillis()
                    
                    // 获取当前编辑器的计数和最后按键时间
                    var currentCount = editorPressCounts.getOrDefault(editor, 0)
                    val lastTime = editorLastPressTimes.getOrDefault(editor, 0L)
                    
                    // 如果超过超时时间，重置计数
                    if (currentTime - lastTime > RESET_TIMEOUT_MS) {
                        currentCount = 0
                    }
                    
                    currentCount++
                    editorPressCounts[editor] = currentCount
                    editorLastPressTimes[editor] = currentTime
                    
                    if (currentCount >= PRESS_THRESHOLD) {
                        // 使用invokeLater确保在EDT中运行
                        SwingUtilities.invokeLater {
                            handleTripleSlashAction(project, editor)
                        }
                        editorPressCounts[editor] = 0 // 重置计数器
                    }
                } else {
                    // 按了其他键，重置当前编辑器的计数器
                    editorPressCounts[editor] = 0
                }
            }
        }
        
        editor.contentComponent.addKeyListener(keyAdapter)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        // 当编辑器释放时，移除键盘监听器
        val editor = event.editor
        editor.contentComponent.removeKeyListener(
            editor.contentComponent.keyListeners.lastOrNull { it is KeyAdapter }
        )
    }

    private fun handleTripleSlashAction(project: Project, editor: Editor) {
        // 获取服务中保存的文件路径
        val service = project.getService(FileStateService::class.java)
        val selectedFilePath = service.getFilePath()

        if (selectedFilePath.isEmpty()) {
            return
        }

        val file = java.io.File(selectedFilePath)
        if (!file.exists() || file.isDirectory) {
            return
        }

        try {
            val content = String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(selectedFilePath)))
            val fileName = file.name
            
            // 从存储的位置开始读取内容
            val startPosition = service.getContentPosition()
            val adjustedContent = if (startPosition < content.length) {
                content.substring(startPosition)
            } else {
                content  // 如果位置超出范围，返回完整内容
            }
            
            // 显示弹窗
            FileContentPopup.show(editor, adjustedContent, fileName)
            
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}