package com.github.aidarkhusainov.reqrun.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware

class ReqRunEditorPopupGroup :
    DefaultActionGroup(),
    DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val visible = project != null && editor != null && file.isReqRunHttpFile()
        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val manager = ActionManager.getInstance()
        val actions =
            listOfNotNull(
                manager.getAction("com.github.aidarkhusainov.reqrun.actions.RunHttpRequestAction"),
                manager.getAction("com.github.aidarkhusainov.reqrun.actions.RunHttpRequestsGroupAction"),
                manager.getAction("com.github.aidarkhusainov.reqrun.actions.CopyCurlAction"),
                manager.getAction("com.github.aidarkhusainov.reqrun.actions.PasteCurlAction"),
            )
        if (actions.isEmpty()) return emptyArray()
        return arrayOf(Separator.getInstance(), *actions.toTypedArray())
    }
}
