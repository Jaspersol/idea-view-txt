package com.example.notetextplugin

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
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComboBox

class MyToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    class MyToolWindow(private val project: Project) {
        val filePathField = JBTextField("")
        private val content = JBPanel<JBPanel<*>>().apply {
            layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT)
            val label = JBLabel("选择文件：")
            filePathField.isEditable = false  // 只读模式，防止直接编辑路径
            
            // 初始化时从服务加载上次保存的路径
            val service = project.getService(FileStateService::class.java)
            filePathField.text = service.getFilePath()
            
            // 设置显示宽度限制和文本截断
            filePathField.preferredSize = Dimension(200, filePathField.preferredSize.height)  // 设置首选宽度200像素
            filePathField.maximumSize = Dimension(200, filePathField.preferredSize.height)  // 设置最大宽度为200像素
            filePathField.toolTipText = filePathField.text  // 显示完整路径的提示
            
            // 添加快捷键切换功能
            val shortcutLabel = JBLabel("弹窗快捷键（三连击）：")
            
            // 定义常用快捷键选项
            val shortcutOptions = arrayOf(
                KeyEvent.VK_SLASH,    // /
                KeyEvent.VK_PERIOD,   // .
                KeyEvent.VK_COMMA,    // ,
                KeyEvent.VK_SEMICOLON, // ;
                KeyEvent.VK_QUOTE,    // '
                KeyEvent.VK_BACK_QUOTE, // `
                KeyEvent.VK_MINUS,    // -
                KeyEvent.VK_EQUALS,   // =
                KeyEvent.VK_OPEN_BRACKET,  // [
                KeyEvent.VK_CLOSE_BRACKET, // ]
                KeyEvent.VK_BACK_SLASH     // backslash
            )
            
            // 创建下拉选择框，显示快捷键的文本表示
            val shortcutComboBox = JComboBox<String>(shortcutOptions.map { KeyEvent.getKeyText(it) }.toTypedArray())
            shortcutComboBox.preferredSize = Dimension(100, shortcutComboBox.preferredSize.height)
            
            // 从服务加载当前快捷键并设置选中项
            val currentShortcut = service.getPopupShortcut()
            val currentShortcutText = KeyEvent.getKeyText(currentShortcut)
            shortcutComboBox.selectedItem = currentShortcutText
            
            val setShortcutButton = JButton("设置快捷键").apply {
                addActionListener {
                    val selectedText = shortcutComboBox.selectedItem as String
                    val selectedIndex = shortcutComboBox.selectedIndex
                    val keyCode = shortcutOptions[selectedIndex]
                    
                    // 保存新的快捷键到服务
                    service.setPopupShortcut(keyCode)
                    
                    // 显示成功消息
                    Messages.showMessageDialog(
                        project,
                        "快捷键已设置为: $selectedText",
                        "设置成功",
                        Messages.getInformationIcon()
                    )
                }
            }
            
            // 添加手动指定索引功能
            val indexLabel = JBLabel("当前索引：")
            val indexField = JBTextField("")
            indexField.preferredSize = Dimension(100, indexField.preferredSize.height)
            
            // 从服务加载当前索引位置并显示
            val currentIndex = service.getContentPosition()
            indexField.text = currentIndex.toString()
            
            // 添加刷新按钮，用于同步弹窗中更新的索引位置
            val refreshIndexButton = JButton("刷新").apply {
                addActionListener {
                    // 从服务重新加载当前索引位置
                    val latestIndex = service.getContentPosition()
                    indexField.text = latestIndex.toString()
                }
            }
            
            val setIndexButton = JButton("更新索引").apply {
                addActionListener {
                    try {
                        val newIndex = indexField.text.toInt()
                        if (newIndex >= 0) {
                            // 保存新的索引位置到服务
                            service.setContentPosition(newIndex)
                            
                            // 显示成功消息
                            Messages.showMessageDialog(
                                project,
                                "索引已更新为: $newIndex",
                                "设置成功",
                                Messages.getInformationIcon()
                            )
                        } else {
                            Messages.showMessageDialog(
                                project,
                                "索引值必须大于等于0",
                                "输入错误",
                                Messages.getWarningIcon()
                            )
                        }
                    } catch (e: NumberFormatException) {
                        Messages.showMessageDialog(
                            project,
                            "请输入有效的数字",
                            "输入错误",
                            Messages.getWarningIcon()
                        )
                    }
                }
            }
            
            add(label)
            add(filePathField)
            add(JButton("浏览").apply {
                addActionListener {
                    val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                    val chosenFiles = com.intellij.openapi.fileChooser.FileChooser.chooseFiles(descriptor, null, null)

                    if (chosenFiles.isNotEmpty()) {
                        val selectedFile: VirtualFile = chosenFiles[0]
                        filePathField.text = selectedFile.path
                        filePathField.toolTipText = selectedFile.path  // 更新提示为完整路径

                        // 保存选中的文件路径到服务，并重置内容位置索引
                        val service = project.getService(FileStateService::class.java)
                        service.setFilePath(selectedFile.path)
                        service.setContentPosition(0)  // 切换文件时清空索引位置
                    }
                }
            })

            // 添加快捷键切换组件
            add(shortcutLabel)
            add(shortcutComboBox)
            add(setShortcutButton)
            
            // 添加手动指定索引组件
            add(indexLabel)
            add(indexField)
            add(refreshIndexButton)
            add(setIndexButton)
        }

        fun getContent(): JBPanel<JBPanel<*>> = content
    }
}