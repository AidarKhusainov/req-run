package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class ReqRunExecutionService(private val project: Project) {
    private val executions = CopyOnWriteArrayList<ReqRunExecution>()

    fun addExecution(
        request: HttpRequestSpec,
        response: HttpResponsePayload?,
        error: String?,
        source: ReqRunRequestSource? = null
    ): ReqRunExecution {
        val exec = ReqRunExecution(request = request, response = response, error = error, source = source)
        executions.add(exec)
        ApplicationManager.getApplication().invokeLater({
            ApplicationManager.getApplication().messageBus.syncPublisher(ServiceEventListener.TOPIC)
                .handle(ServiceEventListener.ServiceEvent.createResetEvent(ReqRunServiceContributor::class.java))
        }, ModalityState.any())
        return exec
    }

    fun list(): List<ReqRunExecution> {
        return executions.toList()
    }

    fun removeExecution(id: java.util.UUID): Boolean {
        val removed = executions.removeIf { it.id == id }
        if (removed) {
            ApplicationManager.getApplication().invokeLater({
                ApplicationManager.getApplication().messageBus.syncPublisher(ServiceEventListener.TOPIC)
                    .handle(ServiceEventListener.ServiceEvent.createResetEvent(ReqRunServiceContributor::class.java))
            }, ModalityState.any())
        }
        return removed
    }
}
