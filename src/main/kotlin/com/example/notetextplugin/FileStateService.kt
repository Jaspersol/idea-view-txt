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
class FileStateService : PersistentStateComponent<FileStateService.State> {
    
    data class State(
        var lastSelectedFilePath: String = "",
        var lastContentPosition: Int = 0,  // 记录最后读取内容的位置
        var popupShortcut: Int = KeyEvent.VK_SLASH  // 默认快捷键是 '/' 键
    )
    
    private var myState = State()
    
    override fun getState(): State? {
        return myState
    }
    
    override fun loadState(state: State) {
        myState = state
    }
    
    fun setFilePath(path: String) {
        myState.lastSelectedFilePath = path
    }
    
    fun getFilePath(): String {
        return myState.lastSelectedFilePath
    }
    
    fun setContentPosition(position: Int) {
        myState.lastContentPosition = position
    }
    
    fun getContentPosition(): Int {
        return myState.lastContentPosition
    }
    
    fun setPopupShortcut(keyCode: Int) {
        myState.popupShortcut = keyCode
    }
    
    fun getPopupShortcut(): Int {
        return myState.popupShortcut
    }
}