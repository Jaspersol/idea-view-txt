package com.example.viewtxt

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupAdapter
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JList

/**
 * 文件内容预览弹窗。
 *
 * - 定位：弹窗跟在光标右下角，超出屏幕时翻到另一侧并夹在屏幕内，编辑器滚动时同步跟随；
 * - 翻页：滚轮向下为下一页、向上为上一页，页内按随机长度切行；
 * - 持久化：当前内容起始索引写回 [FileStateService]，下次打开从上一次的位置继续阅读。
 */
class FileContentPopup {
    companion object {
        /** 弹窗相对光标的水平偏移，避免压住光标所在的字符 */
        private const val POPUP_OFFSET_X = 70

        /** 弹窗相对光标的垂直偏移，也是上下翻转时的留白 */
        private const val POPUP_OFFSET_Y = 20

        fun show(editor: Editor, content: String, fileName: String) {
            // 翻页始终基于完整原文，所以先把原文留住
            val originalFullContent = content

            var currentStartPosition = 0
            var popupWidth = 400
            var popupHeight = 300
            val project = editor.project
            if (project != null) {
                val service = project.getService(FileStateService::class.java)
                currentStartPosition = service.lastContentPosition
                popupWidth = service.popupWidth
                popupHeight = service.popupHeight
                if (currentStartPosition < 0) {
                    currentStartPosition = 0
                }
                if (currentStartPosition > originalFullContent.length) {
                    currentStartPosition = originalFullContent.length
                }
            }

            val listModel = DefaultListModel<String>()
            val list = JBList(listModel)

            fun updateContent(startPos: Int, charCounts: List<Int>) {
                var currentIndex = startPos
                if (currentIndex < 0) {
                    currentIndex = 0
                }
                if (currentIndex > originalFullContent.length) {
                    currentIndex = originalFullContent.length
                }

                val extractedLines = mutableListOf<String>()
                for (charCount in charCounts) {
                    val remainingLength = originalFullContent.length - currentIndex
                    val actualCharCount = minOf(charCount, remainingLength)
                    val line = originalFullContent.substring(currentIndex, currentIndex + actualCharCount)
                    extractedLines.add(line)
                    currentIndex += actualCharCount
                }

                while (extractedLines.size < 10 && currentIndex < originalFullContent.length) {
                    val charCount = charCounts[extractedLines.size]
                    val remainingLength = originalFullContent.length - currentIndex
                    val actualCharCount = minOf(charCount, remainingLength)
                    val line = originalFullContent.substring(currentIndex, currentIndex + actualCharCount)
                    extractedLines.add(line)
                    currentIndex += actualCharCount
                }

                // 后面还有内容时补一个 "..." 占位行，提示可以继续翻页
                val processedLines = if (currentIndex < originalFullContent.length) {
                    extractedLines + "..."
                } else {
                    extractedLines
                }

                listModel.clear()
                processedLines.forEach { line ->
                    listModel.addElement(line)
                }
            }

            updateContent(currentStartPosition, (1..10).map { (10..24).random() })

            // 弹窗字体跟随 IDE 编辑器的字体设置
            val editorColorsManager = EditorColorsManager.getInstance()
            val fontPreferences = editorColorsManager.globalScheme.fontPreferences
            val fontSize = fontPreferences.getSize(FontPreferences.DEFAULT_FONT_NAME)
            val fontFamily = fontPreferences.fontFamily
            val customFont = Font(fontFamily, Font.PLAIN, fontSize)

            list.cellRenderer = object : ColoredListCellRenderer<String>() {
                override fun customizeCellRenderer(
                    list: JList<out String>,
                    value: String?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    if (index == list.model.size - 1 && value == "...") {
                        // "..." 占位行不挂文件图标，只做灰色提示
                        append("...", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    } else {
                        val fileTypes = listOf(".txt", ".js", ".jpg", ".gitignore", ".json")
                        val randomExt = fileTypes.random()
                        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(randomExt.substring(1))
                        icon = fileType.icon
                        append(value ?: "")
                    }

                    font = customFont
                }
            }

            list.preferredSize = Dimension(popupWidth, popupHeight)

            val scrollPane = JBScrollPane(list)
            scrollPane.preferredSize = Dimension(popupWidth - 20, popupHeight - 20)
            scrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
            scrollPane.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER

            val panel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(scrollPane)
            }

            // 每页的切行长度随机生成，用来模拟长文本逐页阅读的效果；
            // 步长必须记下来，翻回上一页时复用同一批步长才能回到原来的切分点
            var lastCharCounts = (1..10).map { (10..24).random() }
            var lastPageSize = lastCharCounts.sum()
            currentStartPosition = currentStartPosition + lastPageSize
            list.addMouseWheelListener { e ->
                val (newPageSize, newCharCounts) = if (e.wheelRotation > 0) {
                    // 滚轮向下：下一页 → 重新生成随机步长，并记为“上一次步长”
                    val charCounts = (1..10).map { (10..24).random() }
                    val pageSize = charCounts.sum()
                    lastPageSize = pageSize
                    lastCharCounts = charCounts
                    Pair(pageSize, charCounts)
                } else {
                    // 滚轮向上：上一页 → 复用上一次的步长，不重新生成
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

                if (project != null) {
                    val service = project.getService(FileStateService::class.java)
                    service.lastContentPosition = currentStartPosition
                }

                e.consume()
            }

            val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, list)
                .setRequestFocus(false) // 不抢焦点，光标留在编辑器里
                .setCancelKeyEnabled(true) // ESC 关闭
                .setCancelOnClickOutside(true) // 点击外部关闭
                .setResizable(true)
                .setMovable(true)
                .createPopup()

            fun updatePopupPosition() {
                fun isPopupWithinEditorBounds(editor: Editor, popup: JBPopup): Boolean {
                    val editorComponent = editor.component
                    val editorBounds = editorComponent.visibleRect

                    val popupLocation = popup.locationOnScreen
                    val popupSize = popup.size
                    val popupRect = Rectangle(popupLocation.x, popupLocation.y, popupSize.width, popupSize.height)

                    val editorLocationOnScreen = editorComponent.locationOnScreen
                    val editorScreenBounds = Rectangle(
                        editorLocationOnScreen.x,
                        editorLocationOnScreen.y,
                        editorBounds.width,
                        editorBounds.height,
                    )

                    return popupRect.intersects(editorScreenBounds)
                }

                runCatching {
                    val caretModel = editor.caretModel
                    val visualPos = caretModel.visualPosition
                    val editorComponent = editor.component
                    val xy = editor.visualPositionToXY(visualPos)
                    val scrollModel = editor.scrollingModel

                    val screenSize = Toolkit.getDefaultToolkit().screenSize
                    val screenBounds = editorComponent.graphicsConfiguration.bounds
                    val actualScreenWidth = screenBounds.width

                    // 先显示一次才能拿到弹窗的真实尺寸
                    if (!popup.isVisible) {
                        popup.show(RelativePoint.getNorthWestOf(editorComponent))
                    }

                    val popupWidth = popup.size.width
                    val popupHeight = popup.size.height

                    val cursorX = xy.x - scrollModel.horizontalScrollOffset
                    val cursorY = xy.y - scrollModel.verticalScrollOffset

                    // 默认放在光标右下角
                    var popupX = cursorX + POPUP_OFFSET_X
                    var popupY = cursorY + POPUP_OFFSET_Y

                    // 右侧或底部放不下时翻到光标另一侧，再夹进屏幕范围
                    if (popupX + popupWidth > actualScreenWidth) {
                        popupX = maxOf(0, cursorX - popupWidth)
                    }
                    if (popupY + popupHeight > screenSize.height) {
                        popupY = maxOf(0, cursorY - popupHeight - POPUP_OFFSET_Y)
                    }
                    if (popupX < 0) {
                        popupX = 0
                    }
                    if (popupY < 0) {
                        popupY = 0
                    }

                    val adjustedPoint = RelativePoint(
                        editorComponent,
                        Point(popupX, popupY),
                    )

                    if (!popup.isVisible) {
                        popup.show(adjustedPoint)
                    } else if (isPopupWithinEditorBounds(editor, popup)) {
                        popup.setLocation(adjustedPoint.screenPoint)
                    } else {
                        popup.cancel()
                    }
                }.onFailure {
                    // 定位失败时保持弹窗在原位，不打断用户
                }
            }

            val scrollingListener = object : VisibleAreaListener {
                override fun visibleAreaChanged(e: VisibleAreaEvent) {
                    if (!popup.isDisposed) {
                        updatePopupPosition()
                    }
                }
            }

            // 弹窗关闭后编辑器仍在，必须自己摘掉监听器，否则它会一直引用已销毁的弹窗
            popup.addListener(object : JBPopupAdapter() {
                override fun onClosed(event: LightweightWindowEvent) {
                    editor.scrollingModel.removeVisibleAreaListener(scrollingListener)
                }
            })

            updatePopupPosition()

            editor.scrollingModel.addVisibleAreaListener(scrollingListener)
        }
    }
}
