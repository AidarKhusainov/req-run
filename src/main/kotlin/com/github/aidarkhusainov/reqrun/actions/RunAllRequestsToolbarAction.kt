package com.github.aidarkhusainov.reqrun.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class RunAllRequestsToolbarAction : AnAction("Run All Requests", null, AllIcons.Actions.Execute), DumbAware {
    private val delegate = RunHttpRequestsGroupAction()

    override fun getActionUpdateThread(): ActionUpdateThread = delegate.actionUpdateThread

    override fun update(e: AnActionEvent) {
        delegate.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        delegate.actionPerformed(e)
    }
}
