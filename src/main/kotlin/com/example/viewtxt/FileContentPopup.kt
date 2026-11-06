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

            var initialStartPosition = 0
            var popupWidth = 400
            var popupHeight = 300
            val project = editor.project
            if (project != null) {
                val service = project.getService(FileStateService::class.java)
                initialStartPosition = service.lastContentPosition
                popupWidth = service.popupWidth
                popupHeight = service.popupHeight
            }
            // 索引越界（例如上次正好停在文末）时从头开始，否则会渲染出一页空行
            if (initialStartPosition !in 0 until originalFullContent.length) {
                initialStartPosition = 0
            }

            val listModel = DefaultListModel<String>()
            val list = JBList(listModel)

            /*
             * 每页的切行长度随机生成（10 行、每行 10~24 个字符），但一页一旦生成就固定下来：
             * 起点记在 pageStarts，切分记在 pageLayouts。前进用“上一页起点 + 上一页步长”，
             * 回翻直接复用上一页记录的起点和切分，渲染与推进始终用同一组数据。
             */
            fun randomLayout(): List<Int> = (1..10).map { (10..24).random() }

            val pageStarts = mutableListOf(initialStartPosition)
            val pageLayouts = mutableListOf(randomLayout())
            var pageIndex = 0

            // 这一页之后的下一个起点；已经到文末时就是文件长度
            fun nextPageStart(): Int {
                val nextStart = pageStarts[pageIndex] + pageLayouts[pageIndex].sum()
                return minOf(nextStart, originalFullContent.length)
            }

            // 渲染一页；到文末时提前结束，不再补空行
            fun renderPage(startPos: Int, charCounts: List<Int>) {
                var currentIndex = startPos
                val extractedLines = mutableListOf<String>()
                for (charCount in charCounts) {
                    if (currentIndex >= originalFullContent.length) {
                        break
                    }
                    val actualCharCount = minOf(charCount, originalFullContent.length - currentIndex)
                    extractedLines.add(originalFullContent.substring(currentIndex, currentIndex + actualCharCount))
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

            renderPage(pageStarts[pageIndex], pageLayouts[pageIndex])

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

            list.addMouseWheelListener { e ->
                if (e.wheelRotation > 0) {
                    // 滚轮向下：下一页。新页起点 = 上一页起点 + 上一页步长，与首屏渲染用同一套算法
                    val nextStart = nextPageStart()
                    if (nextStart < originalFullContent.length) {
                        // 第一次走到这里才生成新页；回翻后再前进复用已记录的页，切分不变
                        if (pageIndex == pageStarts.lastIndex) {
                            pageStarts.add(nextStart)
                            pageLayouts.add(randomLayout())
                        }
                        pageIndex++
                        renderPage(pageStarts[pageIndex], pageLayouts[pageIndex])
                    }
                    // 已经到文末时滚轮向下不做任何事，避免出现一页空行
                } else if (e.wheelRotation < 0) {
                    // 滚轮向上：回到上一页真实记录的起点（而不是当前起点减两页步长）
                    if (pageIndex > 0) {
                        pageIndex--
                        renderPage(pageStarts[pageIndex], pageLayouts[pageIndex])
                    }
                }

                if (project != null) {
                    // 保存下次展示的起点：通常是下一页开头；已经到文末时停在当前页开头
                    val nextStart = nextPageStart()
                    val savedPosition = if (nextStart < originalFullContent.length) nextStart else pageStarts[pageIndex]
                    project.getService(FileStateService::class.java).lastContentPosition = savedPosition
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
