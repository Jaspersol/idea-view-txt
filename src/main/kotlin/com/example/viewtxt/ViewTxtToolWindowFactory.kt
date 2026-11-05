package com.example.viewtxt

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox

/**
 * “View Txt” 工具窗口工厂，工具窗口 id 见 plugin.xml 中的 `MyToolWindow`。
 */
class ViewTxtToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val viewTxtToolWindow = ViewTxtToolWindow(project)
        val content = ContentFactory.getInstance().createContent(viewTxtToolWindow.content, null, false)
        toolWindow.contentManager.addContent(content)
    }

    /**
     * 设置页界面：文件路径、连击快捷键、起始索引、弹窗宽高四组设置。
     *
     * 所有设置都即时写入 [FileStateService]，因此改完不需要额外的保存动作；
     * 切换文件时会顺带把索引置 0，否则旧索引会落到另一个文件的内容中间。
     */
    class ViewTxtToolWindow(private val project: Project) {
        val filePathField = JBTextField("")
        val content = JBPanel<JBPanel<*>>().apply {
            layout = FlowLayout(FlowLayout.LEFT, 0, 0) // 水平和垂直间距都为 0
            border = BorderFactory.createEmptyBorder(5, 10, 5, 10) // 减少外边距
            val label = JBLabel("选择文件：")
            filePathField.isEditable = true // 允许直接编辑路径

            // 初始化时从服务加载上次保存的路径
            val service = project.getService(FileStateService::class.java)
            filePathField.text = service.lastSelectedFilePath

            // 路径输入框限制宽度，完整路径放到 tooltip 里
            filePathField.preferredSize = Dimension(200, filePathField.preferredSize.height)
            filePathField.maximumSize = Dimension(200, filePathField.preferredSize.height)
            filePathField.toolTipText = filePathField.text

            val shortcutLabel = JBLabel("弹窗快捷键（三连击）：")

            val shortcutOptions = arrayOf(
                KeyEvent.VK_SLASH, // /
                KeyEvent.VK_PERIOD, // .
                KeyEvent.VK_COMMA, // ,
                KeyEvent.VK_SEMICOLON, // ;
                KeyEvent.VK_QUOTE, // '
                KeyEvent.VK_BACK_QUOTE, // `
                KeyEvent.VK_MINUS, // -
                KeyEvent.VK_EQUALS, // =
                KeyEvent.VK_OPEN_BRACKET, // [
                KeyEvent.VK_CLOSE_BRACKET, // ]
                KeyEvent.VK_BACK_SLASH, // backslash
            )

            val shortcutComboBox = JComboBox<String>(shortcutOptions.map { KeyEvent.getKeyText(it) }.toTypedArray())
            shortcutComboBox.preferredSize = Dimension(100, shortcutComboBox.preferredSize.height)
            shortcutComboBox.selectedItem = KeyEvent.getKeyText(service.popupShortcut)

            val setShortcutButton = JButton("设置快捷键").apply {
                addActionListener {
                    val selectedText = shortcutComboBox.selectedItem as String
                    val keyCode = shortcutOptions[shortcutComboBox.selectedIndex]

                    service.popupShortcut = keyCode

                    Messages.showMessageDialog(
                        project,
                        "快捷键已设置为: $selectedText",
                        "设置成功",
                        Messages.getInformationIcon(),
                    )
                }
            }

            val indexLabel = JBLabel("当前索引：")
            val indexField = JBTextField("")
            indexField.preferredSize = Dimension(100, indexField.preferredSize.height)
            indexField.text = service.lastContentPosition.toString()

            // 弹窗里翻页后索引会变，刷新按钮让输入框跟上
            val refreshIndexButton = JButton("刷新").apply {
                addActionListener {
                    indexField.text = service.lastContentPosition.toString()
                }
            }

            val setIndexButton = JButton("更新索引").apply {
                addActionListener {
                    try {
                        val newIndex = indexField.text.toInt()
                        if (newIndex >= 0) {
                            service.lastContentPosition = newIndex

                            Messages.showMessageDialog(
                                project,
                                "索引已更新为: $newIndex",
                                "设置成功",
                                Messages.getInformationIcon(),
                            )
                        } else {
                            Messages.showMessageDialog(
                                project,
                                "索引值必须大于等于0",
                                "输入错误",
                                Messages.getWarningIcon(),
                            )
                        }
                    } catch (_: NumberFormatException) {
                        Messages.showMessageDialog(
                            project,
                            "请输入有效的数字",
                            "输入错误",
                            Messages.getWarningIcon(),
                        )
                    }
                }
            }

            // 用垂直 BoxLayout 精确控制各行的垂直间距
            val verticalPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            val pathPanel = JBPanel<JBPanel<*>>().apply {
                layout = FlowLayout(FlowLayout.LEFT, 5, 0)
                add(label)
                add(filePathField)
                add(
                    JButton("浏览").apply {
                        addActionListener {
                            val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                            val chosenFiles = FileChooser.chooseFiles(descriptor, null, null)

                            if (chosenFiles.isNotEmpty()) {
                                val selectedFile: VirtualFile = chosenFiles[0]
                                filePathField.text = selectedFile.path
                                filePathField.toolTipText = selectedFile.path

                                // 换文件后旧索引没有意义，一并归零
                                service.lastSelectedFilePath = selectedFile.path
                                service.lastContentPosition = 0
                            }
                        }
                    },
                )
                add(
                    JButton("保存路径").apply {
                        addActionListener {
                            val newPath = filePathField.text
                            if (newPath.isNotEmpty()) {
                                service.lastSelectedFilePath = newPath
                                service.lastContentPosition = 0

                                Messages.showMessageDialog(
                                    project,
                                    "路径已保存：$newPath",
                                    "保存成功",
                                    Messages.getInformationIcon(),
                                )
                            } else {
                                Messages.showMessageDialog(
                                    project,
                                    "路径不能为空",
                                    "输入错误",
                                    Messages.getWarningIcon(),
                                )
                            }
                        }
                    },
                )
            }
            verticalPanel.add(pathPanel)

            // 很小的垂直间距，避免各组设置挤在一起
            verticalPanel.add(
                JBPanel<JBPanel<*>>().apply {
                    preferredSize = Dimension(0, 2)
                },
            )

            val shortcutPanel = JBPanel<JBPanel<*>>().apply {
                layout = FlowLayout(FlowLayout.LEFT, 5, 0)
                add(shortcutLabel)
                add(shortcutComboBox)
                add(setShortcutButton)
            }
            verticalPanel.add(shortcutPanel)

            verticalPanel.add(
                JBPanel<JBPanel<*>>().apply {
                    preferredSize = Dimension(0, 2)
                },
            )

            val indexPanel = JBPanel<JBPanel<*>>().apply {
                layout = FlowLayout(FlowLayout.LEFT, 5, 0)
                add(indexLabel)
                add(indexField)
                add(refreshIndexButton)
                add(setIndexButton)
            }
            verticalPanel.add(indexPanel)

            verticalPanel.add(
                JBPanel<JBPanel<*>>().apply {
                    preferredSize = Dimension(0, 2)
                },
            )

            val sizePanel = JBPanel<JBPanel<*>>().apply {
                layout = FlowLayout(FlowLayout.LEFT, 5, 0)
                val widthLabel = JBLabel("弹窗宽度：")
                val widthField = JBTextField("")
                widthField.preferredSize = Dimension(100, widthField.preferredSize.height)

                val heightLabel = JBLabel("弹窗高度：")
                val heightField = JBTextField("")
                heightField.preferredSize = Dimension(100, heightField.preferredSize.height)

                widthField.text = service.popupWidth.toString()
                heightField.text = service.popupHeight.toString()

                val setSizeButton = JButton("设置宽高").apply {
                    addActionListener {
                        try {
                            val newWidth = widthField.text.toInt()
                            val newHeight = heightField.text.toInt()
                            if (newWidth > 0 && newHeight > 0) {
                                service.popupWidth = newWidth
                                service.popupHeight = newHeight

                                Messages.showMessageDialog(
                                    project,
                                    "弹窗宽高已设置为: ${newWidth}x$newHeight",
                                    "设置成功",
                                    Messages.getInformationIcon(),
                                )
                            } else {
                                Messages.showMessageDialog(
                                    project,
                                    "宽高值必须大于0",
                                    "输入错误",
                                    Messages.getWarningIcon(),
                                )
                            }
                        } catch (_: NumberFormatException) {
                            Messages.showMessageDialog(
                                project,
                                "请输入有效的数字",
                                "输入错误",
                                Messages.getWarningIcon(),
                            )
                        }
                    }
                }

                add(widthLabel)
                add(widthField)
                add(heightLabel)
                add(heightField)
                add(setSizeButton)
            }
            verticalPanel.add(sizePanel)

            add(verticalPanel)
        }
    }
}
