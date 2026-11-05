package com.example.viewtxt

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.WeakHashMap
import javax.swing.SwingUtilities

/**
 * 编辑器按键监听：同一按键（默认 `/`，见 [FileStateService.popupShortcut]）连按 [PRESS_THRESHOLD] 次即弹出内容预览。
 *
 * 计数与最后按键时间按编辑器存放在 [WeakHashMap] 中，编辑器对象被回收时计数随之释放，避免长开 IDE 后泄漏；
 * [RESET_TIMEOUT_MS] 内没有再按同一个键就重置计数，否则相隔很久的按键会被误判成连击。
 */
class KeyTypedListener : EditorFactoryListener {
    companion object {
        private const val PRESS_THRESHOLD = 3
        private const val RESET_TIMEOUT_MS = 2000
    }

    private val editorPressCounts = WeakHashMap<Editor, Int>()
    private val editorLastPressTimes = WeakHashMap<Editor, Long>()

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        val keyAdapter = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val service = project.getService(FileStateService::class.java)
                val targetKey = service.popupShortcut

                if (e.keyCode == targetKey) {
                    val currentTime = System.currentTimeMillis()
                    var currentCount = editorPressCounts.getOrDefault(editor, 0)
                    val lastTime = editorLastPressTimes.getOrDefault(editor, 0L)

                    // 两次按键间隔超时，说明连击已经中断
                    if (currentTime - lastTime > RESET_TIMEOUT_MS) {
                        currentCount = 0
                    }

                    currentCount++
                    editorPressCounts[editor] = currentCount
                    editorLastPressTimes[editor] = currentTime

                    if (currentCount >= PRESS_THRESHOLD) {
                        // 按键回调里不能直接动 UI，交给 EDT 执行
                        SwingUtilities.invokeLater {
                            handleShortcutTriggered(project, editor)
                        }
                        editorPressCounts[editor] = 0
                    }
                } else {
                    // 按了其他键，本次连击作废
                    editorPressCounts[editor] = 0
                }
            }
        }

        editor.contentComponent.addKeyListener(keyAdapter)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        // 编辑器组件会被复用，不摘掉监听器会重复挂载
        val editor = event.editor
        editor.contentComponent.removeKeyListener(
            editor.contentComponent.keyListeners.lastOrNull { it is KeyAdapter },
        )
    }

    private fun handleShortcutTriggered(project: Project, editor: Editor) {
        val selectedFilePath = project.getService(FileStateService::class.java).lastSelectedFilePath
        if (selectedFilePath.isEmpty()) {
            return
        }

        val file = File(selectedFilePath)
        if (!file.exists() || file.isDirectory) {
            return
        }

        try {
            val content = String(Files.readAllBytes(Paths.get(selectedFilePath)))
            FileContentPopup.show(editor, content, file.name)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
