package com.github.aidarkhusainov.reqrun.ui

import com.github.aidarkhusainov.reqrun.settings.ReqRunResponseViewSettings
import com.github.aidarkhusainov.reqrun.settings.ResponseViewMode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareToggleAction

class ResponseViewSettingsActionGroup(
    private val viewer: ResponseViewer,
    private val settings: ReqRunResponseViewSettings,
) : DefaultActionGroup("Response View Settings", true) {
    init {
        templatePresentation.icon = AllIcons.General.GearPlain
        add(ShowLineNumbersAction(viewer, settings))
        add(ShowRequestMethodAction(settings))
        add(FoldHeadersByDefaultAction(viewer, settings))
        addSeparator()
        add(ViewAsActionGroup(viewer, settings))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private class ShowLineNumbersAction(
    private val viewer: ResponseViewer,
    private val settings: ReqRunResponseViewSettings,
) : DumbAwareToggleAction("Show Line Numbers") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = settings.state.showLineNumbers

    override fun setSelected(
        e: AnActionEvent,
        state: Boolean,
    ) {
        settings.state.showLineNumbers = state
        viewer.setLineNumbersShown(state)
    }
}

private class ShowRequestMethodAction(
    private val settings: ReqRunResponseViewSettings,
) : DumbAwareToggleAction("Show Request Method (Rerun to Apply)") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = settings.state.showRequestMethod

    override fun setSelected(
        e: AnActionEvent,
        state: Boolean,
    ) {
        settings.state.showRequestMethod = state
    }
}

private class FoldHeadersByDefaultAction(
    private val viewer: ResponseViewer,
    private val settings: ReqRunResponseViewSettings,
) : DumbAwareToggleAction("Fold Headers of Non-Empty Responses by Default") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = settings.state.foldHeadersByDefault

    override fun setSelected(
        e: AnActionEvent,
        state: Boolean,
    ) {
        settings.state.foldHeadersByDefault = state
        viewer.refreshContent()
    }
}

private class ViewAsActionGroup(
    private val viewer: ResponseViewer,
    private val settings: ReqRunResponseViewSettings,
) : DefaultActionGroup("View As", true) {
    init {
        add(ViewAsToggleAction(ResponseViewMode.AUTO, viewer, settings, "Auto"))
        add(ViewAsToggleAction(ResponseViewMode.TEXT, viewer, settings, "Text"))
        add(ViewAsToggleAction(ResponseViewMode.JSON, viewer, settings, "JSON"))
        add(ViewAsToggleAction(ResponseViewMode.XML, viewer, settings, "XML"))
        add(ViewAsToggleAction(ResponseViewMode.HTML, viewer, settings, "HTML"))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private class ViewAsToggleAction(
    private val mode: ResponseViewMode,
    private val viewer: ResponseViewer,
    private val settings: ReqRunResponseViewSettings,
    text: String,
) : DumbAwareToggleAction(text) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = settings.state.viewMode == mode

    override fun setSelected(
        e: AnActionEvent,
        state: Boolean,
    ) {
        if (!state) return
        settings.state.viewMode = mode
        viewer.setViewMode(mode)
    }
}
