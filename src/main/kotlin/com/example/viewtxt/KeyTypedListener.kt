package com.example.viewtxt

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.util.IdentityHashMap
import java.util.WeakHashMap
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * 编辑器按键监听：同一按键（默认 `/`，见 [FileStateService.popupShortcut]）连按 [PRESS_THRESHOLD] 次即弹出内容预览。
 *
 * 计数与最后按键时间按编辑器存放在 [WeakHashMap] 中，编辑器对象被回收时计数随之释放，避免长开 IDE 后泄漏；
 * [RESET_TIMEOUT_MS] 内没有再按同一个键、按了别的键、或者焦点离开编辑器，计数都会重置，否则相隔很久的按键会被误判成连击。
 *
 * 每个编辑器安装的监听器记录在 [installs] 中，编辑器释放时按记录精确摘除，不会误摘其它组件上别人的 KeyListener。
 */
class KeyTypedListener : EditorFactoryListener {
    companion object {
        private const val PRESS_THRESHOLD = 3
        private const val RESET_TIMEOUT_MS = 2000

        /** 触发弹窗时不接受的修饰键；Shift 不在此列，部分键盘布局要靠 Shift 才能打出目标字符 */
        private const val FORBIDDEN_MODIFIERS = KeyEvent.CTRL_DOWN_MASK or
            KeyEvent.ALT_DOWN_MASK or
            KeyEvent.META_DOWN_MASK or
            KeyEvent.ALT_GRAPH_DOWN_MASK
    }

    /** 一个编辑器上安装的监听器，释放时按这份记录精确摘除 */
    private class EditorInstallation(
        val component: JComponent,
        val keyAdapter: KeyAdapter,
        val focusAdapter: FocusAdapter,
    )

    private val editorPressCounts = WeakHashMap<Editor, Int>()
    private val editorLastPressTimes = WeakHashMap<Editor, Long>()
    private val installs = IdentityHashMap<Editor, EditorInstallation>()

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        val component = editor.contentComponent

        // 同一个编辑器重复收到 editorCreated 时先摘掉旧的，避免监听器叠加导致计数翻倍
        detach(editor)

        // 触发弹窗后，下一次 KEY_TYPED 就是第三次按键产生的字符，要消费掉避免被编辑器插入
        var suppressNextTypedChar = false

        val keyAdapter = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val targetKey = project.getService(FileStateService::class.java).popupShortcut
                val now = System.currentTimeMillis()

                // Ctrl+/ 之类的带修饰键组合、别的按键都让连击作废
                if (e.keyCode != targetKey || e.modifiersEx and FORBIDDEN_MODIFIERS != 0) {
                    resetProgress(editor)
                    suppressNextTypedChar = false
                    return
                }

                // 两次按键间隔超时，说明连击已经中断
                if (now - editorLastPressTimes.getOrDefault(editor, 0L) > RESET_TIMEOUT_MS) {
                    editorPressCounts.remove(editor)
                }

                val currentCount = editorPressCounts.getOrDefault(editor, 0) + 1
                editorPressCounts[editor] = currentCount
                editorLastPressTimes[editor] = now

                if (currentCount >= PRESS_THRESHOLD) {
                    // 第三次按键本身不能落进文档：按下事件与紧随其后的 KEY_TYPED 都消费掉
                    e.consume()
                    suppressNextTypedChar = true
                    editorPressCounts.remove(editor)
                    // 按键回调里不能直接动 UI，交给 EDT 执行
                    SwingUtilities.invokeLater {
                        handleShortcutTriggered(project, editor)
                    }
                }
            }

            override fun keyTyped(e: KeyEvent) {
                if (suppressNextTypedChar) {
                    suppressNextTypedChar = false
                    if (e.keyChar != KeyEvent.CHAR_UNDEFINED) {
                        e.consume()
                    }
                }
            }

            override fun keyReleased(e: KeyEvent) {
                // 没有生成 KEY_TYPED（例如死键）时不要把这个标记留到下一次输入
                suppressNextTypedChar = false
            }
        }

        val focusAdapter = object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                // 焦点离开编辑器，连击重新开始
                resetProgress(editor)
                suppressNextTypedChar = false
            }
        }

        component.addKeyListener(keyAdapter)
        component.addFocusListener(focusAdapter)
        installs[editor] = EditorInstallation(component, keyAdapter, focusAdapter)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        // 编辑器组件会被复用，不摘掉监听器会重复挂载
        detach(event.editor)
    }

    /** 只摘掉本插件为该编辑器安装的那两个监听器，然后丢弃它的连击状态 */
    private fun detach(editor: Editor) {
        val installation = installs.remove(editor) ?: return
        installation.component.removeKeyListener(installation.keyAdapter)
        installation.component.removeFocusListener(installation.focusAdapter)
        resetProgress(editor)
    }

    private fun resetProgress(editor: Editor) {
        editorPressCounts.remove(editor)
        editorLastPressTimes.remove(editor)
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

        // 读取与解码放在后台线程，大文件不会卡住 EDT；弹窗创建回到 EDT
        FileContentLoader.readTextAsync(
            file,
            onSuccess = { content ->
                try {
                    FileContentPopup.show(editor, content, file.name)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            },
            onError = { ex ->
                ex.printStackTrace()
            },
        )
    }
}
