package com.example.notetextplugin

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

class FileContentPopup {
    companion object {
        fun show(editor: Editor, content: String, fileName: String) {
            val textArea = JBTextArea(content)
            textArea.isEditable = false
            textArea.lineWrap = true
            textArea.wrapStyleWord = true
            
            val scrollPane = JBScrollPane(textArea)
            scrollPane.preferredSize = Dimension(600, 400)
            
            val titleLabel = JLabel("文件内容: $fileName")
            titleLabel.border = JBUI.Borders.empty(5)
            
            val panel = JPanel()
            panel.layout = javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS)
            panel.add(titleLabel)
            panel.add(scrollPane)
            
            val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, textArea)
                .setTitle("文件内容预览")
                .setRequestFocus(true)
                .createPopup()
            
            // 在编辑器的当前光标位置附近显示弹窗
            val point = editor.visualPositionToXY(editor.caretModel.visualPosition)
            val adjustedPoint = java.awt.Point(point.x, point.y + 20)
            popup.showInScreenCoordinates(editor.component, adjustedPoint)
        }
    }
}