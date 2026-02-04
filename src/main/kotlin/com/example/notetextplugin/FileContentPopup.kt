package com.example.notetextplugin

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import java.awt.Point
import javax.swing.JPanel

class FileContentPopup {
    companion object {
        fun show(editor: Editor, content: String, fileName: String) {
            val fullContent = "$fileName\n-------------------\n$content"
            val textArea = JBTextArea(fullContent).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
            }

            // 滚动面板保持不变
            val scrollPane = JBScrollPane(textArea).apply {
                preferredSize = Dimension(400, 300)
            }

            val panel = JBPanel<JBPanel<*>>().apply {
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                add(scrollPane)
            }

            val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, textArea)
                .setRequestFocus(false)  // 不获取焦点，光标留在主文件
                .setCancelKeyEnabled(true)  // ESC关闭
                .setCancelOnClickOutside(true)  // 点击外部关闭
                .setResizable(true)
                .setMovable(true)
                .createPopup()

            // 【核心修复】坐标计算：相对坐标转屏幕绝对坐标 + 偏移补偿 + 空值校验
            runCatching {
                val caretModel = editor.caretModel
                val visualPos = caretModel.visualPosition
                // 1. 获取编辑器可视区域内的光标相对坐标
                val relativePoint = editor.visualPositionToXY(visualPos)
                // 2. 转成屏幕绝对坐标（关键：加上编辑器组件的屏幕坐标偏移）
                val editorComponent = editor.component
                val screenX = editorComponent.locationOnScreen.x + relativePoint.x
                val screenY = editorComponent.locationOnScreen.y + relativePoint.y
                // 3. 偏移补偿（右10px + 下10px），避免弹窗遮挡光标，视觉更友好
                val targetPoint = Point(screenX + 70, screenY + 20)
                // 4. 显示弹窗（传入屏幕绝对坐标）
                popup.showInScreenCoordinates(editorComponent, targetPoint)
            }.onFailure {
                // 异常兜底：坐标计算失败时，弹窗显示在编辑器中心，避免不展示
                popup.showInCenterOf(editor.component)
            }
        }
    }
}