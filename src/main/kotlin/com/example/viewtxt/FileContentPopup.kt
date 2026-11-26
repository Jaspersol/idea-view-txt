package com.example.viewtxt

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ScreenUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseWheelEvent
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.SwingUtilities

/**
 * 文件内容预览弹窗。
 *
 * - 定位：弹窗左边缘与光标横向对齐，默认在光标下方，超出屏幕时翻转，编辑器滚动时同步跟随；
 * - 翻页：滚轮向下为下一页、向上为上一页，页内按随机长度切行；
 * - 持久化：当前内容起始索引写回 [FileStateService]，下次打开从上一次的位置继续阅读。
 */
class FileContentPopup {
    companion object {
        /** 弹窗相对光标的垂直偏移，也是上下翻转时的留白 */
        private const val POPUP_OFFSET_Y = 20

        fun show(
            editor: Editor,
            content: String,
            fileName: String,
            triggerOffset: Int,
            onClosed: () -> Unit = {},
        ): FileContentPopup? {
            val editorComponent = editor.contentComponent
            var followCurrentCaret = false

            fun anchorScreenPoint(): Point? {
                if (editor.isDisposed || !editorComponent.isShowing) {
                    return null
                }
                val caret = editor.caretModel.primaryCaret
                val point = if (followCurrentCaret || caret.offset == triggerOffset) {
                    editor.visualPositionToXY(caret.visualPosition)
                } else {
                    editor.offsetToXY(triggerOffset.coerceAtMost(editor.document.textLength))
                }
                val caretBounds = Rectangle(point.x, point.y, 1, editor.lineHeight)
                if (!editor.scrollingModel.visibleArea.intersects(caretBounds)) {
                    return null
                }
                SwingUtilities.convertPointToScreen(point, editorComponent)
                return point
            }

            val initialAnchor = anchorScreenPoint() ?: return null

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
            val fileType = fileTypeFor(fileName)

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

            fun handleMouseWheel(e: MouseWheelEvent) {
                val rotation = if (e.preciseWheelRotation != 0.0) {
                    e.preciseWheelRotation
                } else {
                    e.wheelRotation.toDouble()
                }

                if (rotation > 0) {
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
                } else if (rotation < 0) {
                    // 滚轮向上：回到上一页真实记录的起点（而不是当前起点减两页步长）
                    if (pageIndex > 0) {
                        pageIndex--
                        renderPage(pageStarts[pageIndex], pageLayouts[pageIndex])
                    }
                }

                if (project != null) {
                    // 保存当前页起点：重开弹窗时回到正在阅读的这一页，而不是跳到下一页
                    project.getService(FileStateService::class.java).lastContentPosition = pageStarts[pageIndex]
                }

                e.consume()
            }

            // 注册在多个层级，避免 JScrollPane 或 popup 层吞掉滚轮事件
            list.addMouseWheelListener(::handleMouseWheel)
            scrollPane.addMouseWheelListener(::handleMouseWheel)
            panel.addMouseWheelListener(::handleMouseWheel)

            val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, list)
                .setRequestFocus(false) // 不抢焦点，光标留在编辑器里
                .setCancelKeyEnabled(true) // ESC 关闭
                .setCancelOnClickOutside(true) // 点击外部关闭
                .setLocateByContent(false)
                .setResizable(true)
                .setMovable(true)
                .createPopup()

            popup.showInScreenCoordinates(editorComponent, popupLocation(initialAnchor, popup.content.preferredSize))
            if (!popup.isVisible) {
                if (!popup.isDisposed) {
                    Disposer.dispose(popup)
                }
                return null
            }

            var positionUpdatePending = false
            fun schedulePopupPositionUpdate() {
                if (positionUpdatePending) {
                    return
                }
                positionUpdatePending = true

                SwingUtilities.invokeLater {
                    positionUpdatePending = false
                    if (!popup.isDisposed && popup.isVisible) {
                        val anchor = anchorScreenPoint()
                        if (anchor == null) {
                            popup.cancel()
                        } else {
                            popup.setLocation(popupLocation(anchor, popup.size))
                        }
                    }
                }
            }

            editor.scrollingModel.addVisibleAreaListener(VisibleAreaListener { schedulePopupPositionUpdate() }, popup)
            editor.caretModel.addCaretListener(
                object : CaretListener {
                    override fun caretPositionChanged(event: CaretEvent) {
                        followCurrentCaret = true
                        schedulePopupPositionUpdate()
                    }
                },
                popup,
            )
            popup.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    onClosed()
                }
            })
            EditorUtil.disposeWithEditor(editor, popup)

            return FileContentPopup(popup)
        }

        private fun popupLocation(anchor: Point, size: Dimension): Point {
            val screen = ScreenUtil.getScreenRectangle(anchor)
            val width = minOf(size.width, screen.width)
            val height = minOf(size.height, screen.height)
            val maxX = screen.x + screen.width - width
            val maxY = screen.y + screen.height - height
            val below = anchor.y + POPUP_OFFSET_Y
            val x = if (anchor.x <= maxX) anchor.x else anchor.x - width
            val y = if (below <= maxY) below else anchor.y - height - POPUP_OFFSET_Y
            return Point(x.coerceIn(screen.x, maxX), y.coerceIn(screen.y, maxY))
        }

        private fun fileTypeFor(fileName: String): FileType {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return FileTypeManager.getInstance().getFileTypeByExtension(extension)
        }
    }

    private val popup: JBPopup

    private constructor(popup: JBPopup) {
        this.popup = popup
    }

    fun close() {
        popup.cancel()
    }
}
