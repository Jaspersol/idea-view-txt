package com.example.notetextplugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "FileStateService",
    storages = [Storage("noteTextPlugin.xml")]
)
class FileStateService {
    var lastSelectedFilePath: String = ""
    var lastContentPosition: Int = 0  // 记录最后读取内容的位置
    
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