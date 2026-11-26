package com.example.viewtxt

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * 编辑器按键监听：同一按键（默认 `/`，见 [FileStateService.popupShortcut]）连按 [PRESS_THRESHOLD] 次即弹出内容预览。
 *
 * 每个编辑器独立计数，按住键产生的自动重复不计入。连击超时、其他按键或焦点离开都会重置计数。
 * 触发键按普通字符输入，第三个字符由编辑器处理后才记录光标位置。
 */
class KeyTypedListener : EditorFactoryListener {
    companion object {
        private const val PRESS_THRESHOLD = 3
        private const val RESET_TIMEOUT_MS = 2000L

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

    private val installs = IdentityHashMap<Editor, EditorInstallation>()

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        val component = editor.contentComponent

        // 同一个编辑器重复收到 editorCreated 时先摘掉旧的，避免监听器叠加导致计数翻倍
        detach(editor)

        val keyAdapter = object : KeyAdapter() {
            private var pressedKey = KeyEvent.VK_UNDEFINED
            private var sequenceKey = KeyEvent.VK_UNDEFINED
            private var pressCount = 0
            private var firstPressAt = 0L
            private var awaitingTriggerChar = false

            override fun keyPressed(e: KeyEvent) {
                val targetKey = project.getService(FileStateService::class.java).popupShortcut
                if (e.isConsumed || e.keyCode != targetKey || e.modifiersEx and FORBIDDEN_MODIFIERS != 0) {
                    resetSequence()
                    return
                }
                if (pressedKey == e.keyCode) {
                    return
                }
                pressedKey = e.keyCode

                if (pressCount == 0 || sequenceKey != targetKey || e.`when` - firstPressAt > RESET_TIMEOUT_MS) {
                    pressCount = 0
                    firstPressAt = e.`when`
                    sequenceKey = targetKey
                }
                pressCount++
                if (pressCount == PRESS_THRESHOLD) {
                    awaitingTriggerChar = true
                }
            }

            override fun keyTyped(e: KeyEvent) {
                if (!awaitingTriggerChar || e.keyChar == KeyEvent.CHAR_UNDEFINED) {
                    return
                }
                resetSequence()
                val triggerOffset = editor.caretModel.offset
                SwingUtilities.invokeLater {
                    if (installs[editor]?.keyAdapter === this) {
                        FileContentPresenter.show(project, editor, triggerOffset)
                    }
                }
            }

            override fun keyReleased(e: KeyEvent) {
                if (pressedKey == e.keyCode) {
                    pressedKey = KeyEvent.VK_UNDEFINED
                    if (awaitingTriggerChar) {
                        resetSequence()
                    }
                }
            }

            fun reset() {
                pressedKey = KeyEvent.VK_UNDEFINED
                resetSequence()
            }

            private fun resetSequence() {
                pressCount = 0
                awaitingTriggerChar = false
            }
        }

        val focusAdapter = object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                keyAdapter.reset()
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
    }
}
