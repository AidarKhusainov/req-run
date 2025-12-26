package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

class EnvSelectorAction(
    private val project: Project,
    private var file: VirtualFile
) : ComboBoxAction() {
    private val envService = project.getService(ReqRunEnvironmentService::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: file
        val selected = envService.getSelectedEnvironment()
        e.presentation.text = selected ?: NO_ENVIRONMENT
        e.presentation.description = envService.describeEnvPaths(file)
    }

    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {
        val names = ReadAction.compute<List<String>, RuntimeException> {
            envService.getEnvironmentNames(file)
        }
        val group = DefaultActionGroup()
        group.add(envToggleAction(NO_ENVIRONMENT, null))
        for (name in names) {
            group.add(envToggleAction(name, name))
        }
        group.add(Separator.getInstance())
        group.add(openEnvAction(OPEN_SHARED_ENV, isPrivate = false))
        group.add(openEnvAction(OPEN_PRIVATE_ENV, isPrivate = true))
        return group
    }

    companion object {
        private const val NO_ENVIRONMENT = "No Environment"
        private const val OPEN_SHARED_ENV = "Open http-client.env.json"
        private const val OPEN_PRIVATE_ENV = "Open http-client.private.env.json"
    }

    private fun openEnvAction(text: String, isPrivate: Boolean): AnAction =
        object : AnAction(text) {
            override fun actionPerformed(e: AnActionEvent) {
                val path = envService.ensureEnvFile(file, isPrivate) ?: return
                val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return
                VfsUtil.markDirtyAndRefresh(false, false, false, vFile)
                FileEditorManager.getInstance(project).openFile(vFile, true)
            }
        }

    private fun envToggleAction(text: String, envName: String?): ToggleAction =
        object : ToggleAction(text) {
            override fun isSelected(e: AnActionEvent): Boolean =
                envService.getSelectedEnvironment() == envName

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (!state) return
                envService.setSelectedEnvironment(envName)
            }
        }
}
