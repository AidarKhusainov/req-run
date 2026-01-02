package com.github.aidarkhusainov.reqrun.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAwareToggleAction

class ToggleSoftWrapsAction(
    private val viewer: ResponseViewer
) : DumbAwareToggleAction(
    "Soft Wraps",
    "Toggle soft wraps for response view",
    AllIcons.Actions.ToggleSoftWrap
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = viewer.isSoftWrapsEnabled()

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        viewer.setSoftWrapsEnabled(state)
    }
}
