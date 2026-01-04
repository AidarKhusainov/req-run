package com.github.aidarkhusainov.reqrun.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ScrollToTopAction(
    private val viewer: ResponseViewer,
) : DumbAwareAction("Scroll to Top", "Scroll response to the beginning", AllIcons.Actions.MoveUp) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        viewer.scrollToTop()
    }
}

class ScrollToEndAction(
    private val viewer: ResponseViewer,
) : DumbAwareAction("Scroll to End", "Scroll response to the end", AllIcons.Actions.MoveDown) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        viewer.scrollToEnd()
    }
}

class CopyResponseBodyAction(
    private val viewer: ResponseViewer,
) : DumbAwareAction("Copy Response Body", "Copy response body to clipboard", AllIcons.Actions.Copy) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        viewer.copyResponseBodyToClipboard()
    }
}
