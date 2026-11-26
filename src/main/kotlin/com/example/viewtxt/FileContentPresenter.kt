package com.example.viewtxt

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import java.io.File

/**
 * 菜单入口与三连击共用的读取/展示流程，避免两条入口分叉出不同行为。
 */
object FileContentPresenter {
    private const val NOTIFICATION_GROUP_ID = "ViewTxt"

    @Volatile
    private var activePopup: FileContentPopup? = null

    @Volatile
    private var requestSerial: Long = 0

    fun show(project: Project, editor: Editor) {
        val service = project.getService(FileStateService::class.java)
        val selectedFilePath = service.lastSelectedFilePath
        if (selectedFilePath.isEmpty()) {
            notify(project, "没有选择任何文件，请先在工具窗口中选择一个文件。", NotificationType.WARNING)
            return
        }

        val file = File(selectedFilePath)
        if (!file.exists() || file.isDirectory) {
            notify(project, "文件不可用: $selectedFilePath", NotificationType.WARNING)
            return
        }

        val request = ++requestSerial
        val triggerScreenPoint = FileContentPopup.caretScreenPoint(editor)

        FileContentLoader.readTextAsync(
            file,
            project = project,
            onSuccess = { content ->
                if (request != requestSerial || project.isDisposed || editor.isDisposed) {
                    return@readTextAsync
                }
                activePopup?.close()
                activePopup = FileContentPopup.show(
                    editor,
                    content,
                    file.name,
                    triggerScreenPoint,
                    onClosed = {
                        if (request == requestSerial) {
                            activePopup = null
                        }
                    },
                )
            },
            onError = { error ->
                if (request != requestSerial || project.isDisposed) {
                    return@readTextAsync
                }
                notify(
                    project,
                    "读取文件时出错: ${error.message ?: error.javaClass.simpleName}",
                    NotificationType.ERROR,
                )
            },
        )
    }

    private fun notify(project: Project, message: String, type: NotificationType): Unit =
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification("View Txt", message, type)
            .notify(project)
}
