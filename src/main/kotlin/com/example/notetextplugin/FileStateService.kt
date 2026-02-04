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
    
    fun setFilePath(path: String) {
        lastSelectedFilePath = path
    }
    
    fun getFilePath(): String {
        return lastSelectedFilePath
    }
}