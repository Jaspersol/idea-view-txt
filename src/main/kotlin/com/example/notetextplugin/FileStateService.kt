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
        var popupShortcut: Int = KeyEvent.VK_SLASH,  // 默认快捷键是 '/' 键
        var popupWidth: Int = 400,  // 默认弹窗宽度
        var popupHeight: Int = 300  // 默认弹窗高度
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
    
    fun setPopupWidth(width: Int) {
        myState.popupWidth = width
    }
    
    fun getPopupWidth(): Int {
        return myState.popupWidth
    }
    
    fun setPopupHeight(height: Int) {
        myState.popupHeight = height
    }
    
    fun getPopupHeight(): Int {
        return myState.popupHeight
    }
}