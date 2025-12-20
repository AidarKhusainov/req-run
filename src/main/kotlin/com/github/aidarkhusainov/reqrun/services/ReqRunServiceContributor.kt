package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.core.ReqRunExecutor
import com.github.aidarkhusainov.reqrun.icons.ReqRunIcons
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.github.aidarkhusainov.reqrun.ui.ResponseViewer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewManager
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ReqRunServiceContributor : ServiceViewContributor<ReqRunExecution> {
    private val viewers = ConcurrentHashMap<UUID, ResponseViewer>()

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        SimpleDescriptor("Request Run", ReqRunIcons.Api, null)

    override fun getServices(project: Project): List<ReqRunExecution> =
        project.getService(ReqRunExecutionService::class.java).list()

    override fun getServiceDescriptor(project: Project, service: ReqRunExecution): ServiceViewDescriptor {
        val viewer = viewers.computeIfAbsent(service.id) { ResponseViewer(project, service) }
        return ExecutionDescriptor(project, service, viewer) { viewers.remove(service.id) }
    }
}

internal open class SimpleDescriptor(
    private val text: String,
    private val icon: javax.swing.Icon?,
    private val content: javax.swing.JComponent?
) : ServiceViewDescriptor {
    override fun getPresentation(): ItemPresentation = PresentationData(text, null, icon, null)
    override fun getContentComponent(): javax.swing.JComponent? = content
    override fun getId(): String = text
    override fun isVisible(): Boolean = true
}

private class ExecutionDescriptor(
    private val project: Project,
    private val execution: ReqRunExecution,
    private val viewer: ResponseViewer,
    private val removeViewer: () -> Unit
) : ServiceViewDescriptor {
    private val rerunAction: AnAction =
        object : DumbAwareAction("Re-run Request", "Execute this request again", AllIcons.Actions.Rerun) {
            override fun actionPerformed(e: AnActionEvent) {
                val spec = execution.request
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Re-running HTTP request", false) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        try {
                            val response = project.getService(ReqRunExecutor::class.java).execute(spec)
                            val exec = project.getService(ReqRunExecutionService::class.java)
                                .addExecution(spec, response, null, execution.source)
                            ServiceViewManager.getInstance(project)
                                .select(exec, ReqRunServiceContributor::class.java, true, true)
                        } catch (t: Throwable) {
                            val exec = project.getService(ReqRunExecutionService::class.java)
                                .addExecution(spec, null, t.message, execution.source)
                            ReqRunNotifier.error(project, "Request failed: ${t.message ?: "unknown error"}")
                            ServiceViewManager.getInstance(project)
                                .select(exec, ReqRunServiceContributor::class.java, true, true)
                        }
                    }
                })
            }
        }

    override fun getPresentation(): ItemPresentation =
        PresentationData("${execution.request.method} ${execution.request.url}", null, null, null)

    override fun getContentComponent(): javax.swing.JComponent =
        viewer.component

    override fun getId(): String = execution.id.toString()
    override fun isVisible(): Boolean = true

    override fun getToolbarActions(): com.intellij.openapi.actionSystem.ActionGroup =
        DefaultActionGroup(rerunAction)

    override fun getNavigatable(): Navigatable? {
        val source = execution.source ?: return null
        return OpenFileDescriptor(project, source.file, source.offset)
    }

    override fun getRemover(): Runnable = Runnable {
        val removed = project.getService(ReqRunExecutionService::class.java)
            .removeExecution(execution.id)
        if (removed) {
            removeViewer()
        }
    }
}
