package com.example.notetextplugin

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import java.awt.Point
import javax.swing.DefaultListModel
import javax.swing.JList

class FileContentPopup {
    companion object {
        fun show(editor: Editor, content: String, fileName: String) {
            // 创建一个包含原始内容的变量，用于翻页功能
            val originalFullContent = content

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

            // 创建列表模型和列表
            val listModel = DefaultListModel<String>()
            val list = JBList(listModel)

            // 定义翻页函数
            fun updateContent(startPos: Int, charCounts: List<Int>) {
                // 确保起始位置在有效范围内
                var currentIndex = startPos
                if (currentIndex < 0) currentIndex = 0
                if (currentIndex > originalFullContent.length) {
                    currentIndex = originalFullContent.length
                }

                // 更新当前起始位置
                val extractedLines = mutableListOf<String>()

                // 重新加载数据
                for (charCount in charCounts) {
                    val remainingLength = originalFullContent.length - currentIndex
                    val actualCharCount = minOf(charCount, remainingLength)
                    val line = originalFullContent.substring(currentIndex, currentIndex + actualCharCount)
                    extractedLines.add(line)
                    currentIndex += actualCharCount
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
            }

            // 初始化内容
            updateContent(currentStartPosition, (1..10).map { (5..24).random() })

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
                        // 随机选择文件类型图标
                        val fileTypes = listOf(".txt", ".js", ".jpg", ".gitignore", ".json")
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

            // 添加鼠标滚轮监听器实现翻页
            var lastCharCounts = (1..10).map { (5..24).random() }
            var lastPageSize = lastCharCounts.sum()
            currentStartPosition = currentStartPosition + lastPageSize
            list.addMouseWheelListener { e ->
                // 区分下翻（前进）和上翻（回退），复用/生成步长
                val (newPageSize, newCharCounts) = if (e.wheelRotation > 0) {
                    // 向下滚动：向后翻页 → 重新生成随机步长，并保存为“上一次步长”
                    val charCounts = (1..10).map { (5..24).random() }
                    val pageSize = charCounts.sum()
                    lastPageSize = pageSize
                    lastCharCounts = charCounts
                    Pair(pageSize, charCounts)
                } else {
                    // 向上滚动：向前翻页 → 复用上一次下翻的步长，不重新生成
                    Pair(lastPageSize, lastCharCounts)
                }

                if (e.wheelRotation > 0) {
                    updateContent(currentStartPosition, newCharCounts)
                    currentStartPosition = currentStartPosition + newPageSize
                } else {
                    updateContent(currentStartPosition - 2 * newPageSize, newCharCounts)
                    currentStartPosition = currentStartPosition - newPageSize
                }

                // 避免越界
                if (currentStartPosition < 0) {
                    currentStartPosition = 0
                }
                if (currentStartPosition > originalFullContent.length) {
                    currentStartPosition = originalFullContent.length
                }

                // 更新存储的索引位置
                if (project != null) {
                    val service = project.getService(FileStateService::class.java)
                    // 存储当前的起始位置
                    service.setContentPosition(currentStartPosition)
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

            //  更新弹窗位置
            fun updatePopupPosition() {
                // 检查弹窗是否在编辑器边界内
                fun isPopupWithinEditorBounds(editor: Editor, popup: com.intellij.openapi.ui.popup.JBPopup): Boolean {
                    val editorComponent = editor.component
                    val editorBounds = editorComponent.visibleRect

                    // 获取弹窗的位置
                    val popupLocation = popup.locationOnScreen
                    val popupSize = popup.size

                    // 计算弹窗的边界矩形
                    val popupRect =
                        java.awt.Rectangle(popupLocation.x, popupLocation.y, popupSize.width, popupSize.height)

                    // 将编辑器的边界转换为屏幕坐标
                    val editorLocationOnScreen = editorComponent.locationOnScreen
                    val editorScreenBounds = java.awt.Rectangle(
                        editorLocationOnScreen.x,
                        editorLocationOnScreen.y,
                        editorBounds.width,
                        editorBounds.height
                    )

                    // 检查弹窗是否与编辑器的屏幕边界有交集
                    return popupRect.intersects(editorScreenBounds)
                }

                runCatching {
                    val caretModel = editor.caretModel
                    val visualPos = caretModel.visualPosition
                    val editorComponent = editor.component
                    val xy = editor.visualPositionToXY(visualPos)
                    val scrollModel = editor.scrollingModel
                    val relativePoint = RelativePoint(
                        editorComponent,
                        Point(
                            xy.x + 70 - scrollModel.horizontalScrollOffset,
                            xy.y + 20 - scrollModel.verticalScrollOffset
                        )
                    )
                    if (!popup.isVisible)
                        popup.show(relativePoint)
                    else {
                        if (isPopupWithinEditorBounds(editor, popup)) {
                            popup.setLocation(relativePoint.screenPoint)
                        } else {
                            if (popup.isVisible)
                                popup.cancel()
                        }
                    }
                }.onFailure {
                }
            }
            // 监听主编辑器的滚动事件
            val scrollingListener = object : com.intellij.openapi.editor.event.VisibleAreaListener {
                override fun visibleAreaChanged(e: com.intellij.openapi.editor.event.VisibleAreaEvent) {
                    if (popup.isDisposed.not()) {
                        // 重新计算并更新弹窗位置
                        updatePopupPosition()
                    }
                }
            }

            // 弹窗关闭时移除监听器
            popup.addListener(object : com.intellij.openapi.ui.popup.JBPopupAdapter() {
                override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                    editor.scrollingModel.removeVisibleAreaListener(scrollingListener)
                }
            })

            // 设置弹窗位置
            updatePopupPosition()

            // 注册滚动监听器
            editor.scrollingModel.addVisibleAreaListener(scrollingListener)
        }
    }
}