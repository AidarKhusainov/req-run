package com.github.aidarkhusainov.reqrun.testutil

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

fun createActionEvent(
    project: Project?,
    editor: Editor?,
    file: VirtualFile?,
    files: Array<VirtualFile>? = null
): AnActionEvent {
    val dataContext = DataContext { dataId ->
        when (dataId) {
            CommonDataKeys.PROJECT.name -> project
            CommonDataKeys.EDITOR.name -> editor
            CommonDataKeys.VIRTUAL_FILE.name -> file
            CommonDataKeys.VIRTUAL_FILE_ARRAY.name -> files
            else -> null
        }
    }
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, Presentation(), dataContext)
}

fun collectReqRunNotifications(project: Project): List<Notification> =
    NotificationsManager.getNotificationsManager()
        .getNotificationsOfType(Notification::class.java, project)
        .toList()

fun clearReqRunNotifications(project: Project) {
    collectReqRunNotifications(project).forEach { it.expire() }
}
