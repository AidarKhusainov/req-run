package com.github.aidarkhusainov.reqrun.ui

import com.github.aidarkhusainov.reqrun.actions.EnvSelectorAction
import com.github.aidarkhusainov.reqrun.actions.NewRequestGroup
import com.github.aidarkhusainov.reqrun.actions.ReqRunExportGroup
import com.github.aidarkhusainov.reqrun.actions.ReqRunImportGroup
import com.github.aidarkhusainov.reqrun.actions.RunAllRequestsToolbarAction
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

class ReqRunToolbarPanel(
    project: Project,
    editor: TextEditor,
    file: VirtualFile,
) : JPanel(BorderLayout()) {
    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(0, 6)
        val group =
            DefaultActionGroup().apply {
                add(NewRequestGroup())
                addSeparator()
                add(ReqRunExportGroup())
                add(ReqRunImportGroup())
                addSeparator()
                add(RunAllRequestsToolbarAction())
                addSeparator()
                add(EnvSelectorAction(project, file))
            }
        val toolbar =
            ActionManager
                .getInstance()
                .createActionToolbar("ReqRunHttpToolbar.Main", group, true)
        toolbar.targetComponent = editor.component
        toolbar.component.isOpaque = true
        toolbar.component.background = background

        val toolbarHeight = toolbar.component.preferredSize.height
        val examplesLink =
            ActionLink("Examples") {
                BrowserUtil.browse("https://github.com/AidarKhusainov/req-run/tree/main/docs/examples")
            }
        examplesLink.font = toolbar.component.font
        examplesLink.isOpaque = false
        examplesLink.border = JBUI.Borders.empty(0, 6, 0, 6)
        val linkSize = examplesLink.preferredSize
        val sizedLink = Dimension(linkSize.width, toolbarHeight)
        examplesLink.preferredSize = sizedLink
        examplesLink.minimumSize = sizedLink
        examplesLink.maximumSize = sizedLink

        val rightPanel =
            object : JPanel(BorderLayout()) {
                override fun getPreferredSize(): Dimension {
                    val size = super.getPreferredSize()
                    return Dimension(size.width, toolbarHeight)
                }
            }.apply {
                isOpaque = false
                add(examplesLink, BorderLayout.CENTER)
            }

        add(toolbar.component, BorderLayout.CENTER)
        add(rightPanel, BorderLayout.EAST)
    }
}
