package com.example.notetextplugin

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton
import kotlin.random.Random

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
            val label = JBLabel("选择文件：")
            filePathField.isEditable = false  // 只读模式，防止直接编辑路径
            
            // 初始化时从服务加载上次保存的路径
            val service = project.getService(FileStateService::class.java)
            filePathField.text = service.getFilePath()
            
            add(label)
            add(filePathField)
            add(JButton("浏览").apply {
                addActionListener {
                    val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                    val chosenFiles = com.intellij.openapi.fileChooser.FileChooser.chooseFiles(descriptor, null, null)
                    
                    if (chosenFiles.isNotEmpty()) {
                        val selectedFile: VirtualFile = chosenFiles[0]
                        filePathField.text = selectedFile.path
                        
                        // 保存选中的文件路径到服务
                        val service = project.getService(FileStateService::class.java)
                        service.setFilePath(selectedFile.path)
                    }
                }
            })
        }

        fun getContent(): JBPanel<JBPanel<*>> = content
    }
}