package com.example.notetextplugin

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupAdapter
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.LightweightWindowEvent
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
            // 创建一个包含原始内容的变量，用于翻页功能
            val originalFullContent = "\n$content"
                    
            // 从FileStateService获取上次的位置，如果没有则使用0
            var currentStartPosition = 0
            val project = editor.project
            if (project != null) {
                val service = project.getService(FileStateService::class.java)
                currentStartPosition = service.getContentPosition()
                // 确保位置在有效范围内
                if (currentStartPosition < 0) currentStartPosition = 0
                if (currentStartPosition > originalFullContent.length) {
                    currentStartPosition = originalFullContent.length
                }
            }
                    
            // 初始化内容将通过updateContent函数完成
                    
            // 创建列表模型和列表
            val listModel = DefaultListModel<String>()
            val list = JBList(listModel)
            
            // 定义翻页函数
            fun updateContent(startPos: Int) {
                // 确保起始位置在有效范围内
                var adjustedStartPos = startPos
                if (adjustedStartPos < 0) adjustedStartPos = 0
                if (adjustedStartPos > originalFullContent.length) {
                    adjustedStartPos = originalFullContent.length
                }
                
                // 更新当前起始位置
                currentStartPosition = adjustedStartPos
                var currentIndex = adjustedStartPos
                val extractedLines = mutableListOf<String>()
                        
                // 每次翻页都重新生成10个2-24之间的数字作为每行的字数
                val charCounts = (1..10).map { (2..24).random() }
                        
                // 重新加载数据
                for (charCount in charCounts) {
                    if (currentIndex < originalFullContent.length) {
                        val remainingLength = originalFullContent.length - currentIndex
                        val actualCharCount = minOf(charCount, remainingLength)
                        val line = originalFullContent.substring(currentIndex, currentIndex + actualCharCount)
                        extractedLines.add(line)
                        currentIndex += actualCharCount
                    }
                }
                        
                // 如果提取的行数不足10行，补充空行
                while (extractedLines.size < 10 && currentIndex < originalFullContent.length) {
                    val charCount = charCounts[extractedLines.size]
                    val remainingLength = originalFullContent.length - currentIndex
                    val actualCharCount = minOf(charCount, remainingLength)
                    val line = originalFullContent.substring(currentIndex, currentIndex + actualCharCount)
                    extractedLines.add(line)
                    currentIndex += actualCharCount
                }
                        
                // 处理最后的行，如果还有内容则添加省略号
                val processedLines = if (currentIndex < originalFullContent.length) {
                    extractedLines + "..."
                } else {
                    extractedLines
                }
                        
                // 更新列表模型显示新数据
                listModel.clear()
                processedLines.forEach { line ->
                    listModel.addElement(line)
                }
                        
                // 更新存储的索引位置
                if (project != null) {
                    val service = project.getService(FileStateService::class.java)
                    // 存储当前的起始位置
                    service.setContentPosition(adjustedStartPos)
                }
            }
                    
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
                    
            // 初始化内容
            updateContent(currentStartPosition)
                    
            // 添加鼠标滚轮监听器实现翻页
            list.addMouseWheelListener { e ->
                // 每次翻页都重新计算pageSize（但不会使用，因为每行长度都是随机的）
                val charCounts = (1..10).map { (2..24).random() }
                val pageSize = charCounts.sum()
                
                // 计算新的起始位置
                val newStartPos = if (e.wheelRotation > 0) {
                    // 向下滚动：向前翻页
                    minOf(currentStartPosition + pageSize, originalFullContent.length)
                } else {
                    // 向上滚动：向后翻页
                    maxOf(currentStartPosition - pageSize, 0)
                }
                
                // 只有当起始位置发生变化时才更新内容
                if (newStartPos != currentStartPosition) {
                    updateContent(newStartPos)
                }
                
                e.consume()
            }
                    
            val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, list)
                .setRequestFocus(false)  // 不获取焦点，光标留在主文件
                .setCancelKeyEnabled(true)  // ESC关闭
                .setCancelOnClickOutside(true)  // 点击外部关闭
                .setResizable(true)
                .setMovable(true)
                // 移除键盘监听器相关的关闭事件处理
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
                    
            // 更新存储的内容位置
            if (project != null) {
                val service = project.getService(FileStateService::class.java)
                service.setContentPosition(currentStartPosition)
            }
        }
    }
}