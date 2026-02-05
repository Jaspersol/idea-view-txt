package com.example.notetextplugin

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.event.KeyEvent

@State(
    name = "FileStateService",
    storages = [Storage("noteTextPlugin.xml")]
)
@Service(Service.Level.PROJECT)
class FileStateService {
    var lastSelectedFilePath: String = ""
    var lastContentPosition: Int = 0  // 记录最后读取内容的位置
    var popupShortcut: Int = KeyEvent.VK_SLASH  // 默认快捷键是 '/' 键
    
    fun setFilePath(path: String) {
        lastSelectedFilePath = path
    }
    
    fun getFilePath(): String {
        return lastSelectedFilePath
    }
    
    fun setContentPosition(position: Int) {
        lastContentPosition = position
    }
    
    fun getContentPosition(): Int {
        return lastContentPosition
    }
}