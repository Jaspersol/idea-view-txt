package com.example.notetextplugin

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Point
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import com.intellij.ui.SimpleTextAttributes

class FileContentPopup {
    companion object {
        fun show(editor: Editor, content: String, fileName: String) {
            // 构建包含文件名和分隔符的完整内容
            val fullContent = "\n$content"
            
            // 随机生成10个2-24之间的数字作为每行的字数
            val charCounts = (1..10).map { (2..24).random() }
            
            // 根据字数从内容中提取相应的字符
            val extractedLines = mutableListOf<String>()
            var currentIndex = 0
            
            for (charCount in charCounts) {
                if (currentIndex < fullContent.length) {
                    val remainingLength = fullContent.length - currentIndex
                    val actualCharCount = minOf(charCount, remainingLength)
                    val line = fullContent.substring(currentIndex, currentIndex + actualCharCount)
                    extractedLines.add(line)
                    currentIndex += actualCharCount
                }
            }
            
            // 如果提取的行数不足10行，补充空行
            while (extractedLines.size < 10 && currentIndex < fullContent.length) {
                val charCount = charCounts[extractedLines.size]
                val remainingLength = fullContent.length - currentIndex
                val actualCharCount = minOf(charCount, remainingLength)
                val line = fullContent.substring(currentIndex, currentIndex + actualCharCount)
                extractedLines.add(line)
                currentIndex += actualCharCount
            }
            
            // 处理最后的行，如果还有内容则添加省略号
            val processedLines = if (currentIndex < fullContent.length) {
                extractedLines + "..."
            } else {
                extractedLines
            }
            
            val listModel = DefaultListModel<String>()
            processedLines.forEach { line ->
                listModel.addElement(line)
            }
            
            val list = JBList(listModel)
            
            // 自定义渲染器以显示图标
            list.cellRenderer = object : ColoredListCellRenderer<String>() {
                override fun customizeCellRenderer(
                    list: JList<out String>,
                    value: String?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    // 检查是否是最后一行且内容为"..."
                    if (index == list.model.size - 1 && value == "...") {
                        // 最后一行不显示图标，设置为灰色
                        append("...", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    } else {
                        // 随机选择文件类型图标：java, js, jpg, impl
                        val fileTypes = listOf(".java", ".js", ".jpg", ".impl")
                        val randomExt = fileTypes.random()
                        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(randomExt.substring(1))
                        icon = fileType.icon
                        append(value ?: "")
                    }
                }
            }
            
            // 设置列表首选大小
            list.preferredSize = Dimension(400, 300)
            
            val scrollPane = JBScrollPane(list)
            scrollPane.preferredSize = Dimension(380, 280)
            scrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
            scrollPane.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            
            val panel = JBPanel<JBPanel<*>>().apply {
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                add(scrollPane)
            }
            
            val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, list)
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