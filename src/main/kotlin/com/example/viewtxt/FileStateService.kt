package com.example.viewtxt

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.awt.event.KeyEvent

/**
 * 项目级设置服务，状态持久化在项目目录下的 viewTxt.xml 中。
 *
 * 服务在 plugin.xml 中通过 `<projectService serviceImplementation=.../>` 注册（唯一的注册方式，
 * 不要再用 `@Service` 注解重复注册），代码里统一通过 `project.getService(FileStateService::class.java)` 获取。
 *
 * [State] 中各字段即持久化字段，改动字段名会让旧配置失效：
 * - `lastSelectedFilePath`：上次选中/保存的文件路径
 * - `lastContentPosition`：弹窗里当前内容的起始索引，用于下次从上次位置继续
 * - `popupShortcut`：触发弹窗的按键码（见 [KeyEvent]）
 * - `popupWidth` / `popupHeight`：弹窗宽高
 */
@State(
    name = "FileStateService",
    storages = [Storage("viewTxt.xml")],
)
class FileStateService : PersistentStateComponent<FileStateService.State> {
    data class State(
        var lastSelectedFilePath: String = "",
        var lastContentPosition: Int = 0,
        var popupShortcut: Int = KeyEvent.VK_SLASH,
        var popupWidth: Int = 400,
        var popupHeight: Int = 300,
    )

    private var myState = State()

    override fun getState(): State? = myState

    override fun loadState(state: State) {
        myState = state
    }

    var lastSelectedFilePath: String
        get() = myState.lastSelectedFilePath
        set(value) {
            myState.lastSelectedFilePath = value
        }

    var lastContentPosition: Int
        get() = myState.lastContentPosition
        set(value) {
            myState.lastContentPosition = value
        }

    var popupShortcut: Int
        get() = myState.popupShortcut
        set(value) {
            myState.popupShortcut = value
        }

    var popupWidth: Int
        get() = myState.popupWidth
        set(value) {
            myState.popupWidth = value
        }

    var popupHeight: Int
        get() = myState.popupHeight
        set(value) {
            myState.popupHeight = value
        }
}
