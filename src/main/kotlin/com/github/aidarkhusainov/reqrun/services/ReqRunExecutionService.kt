package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class ReqRunExecutionService(private val project: Project) {
    companion object {
        private const val MAX_HISTORY = 200
    }

    private val executions = CopyOnWriteArrayList<ReqRunExecution>()

    fun addExecution(
        request: HttpRequestSpec,
        response: HttpResponsePayload?,
        error: String?,
        source: ReqRunRequestSource? = null
    ): ReqRunExecution {
        val exec = ReqRunExecution(request = request, response = response, error = error, source = source)
        val evictedIds = mutableListOf<java.util.UUID>()
        synchronized(executions) {
            executions.add(exec)
            while (executions.size > MAX_HISTORY) {
                evictedIds += executions.removeAt(0).id
            }
        }
        if (evictedIds.isNotEmpty()) {
            ReqRunServiceContributor.evict(project, evictedIds)
        }
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            project.messageBus.syncPublisher(ServiceEventListener.TOPIC)
                .handle(ServiceEventListener.ServiceEvent.createResetEvent(ReqRunServiceContributor::class.java))
        }, ModalityState.any())
        return exec
    }

    fun list(): List<ReqRunExecution> {
        return executions.toList()
    }

    fun clearAll(): Int {
        val removed = synchronized(executions) {
            val count = executions.size
            executions.clear()
            count
        }
        if (removed > 0) {
            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed) return@invokeLater
                project.messageBus.syncPublisher(ServiceEventListener.TOPIC)
                    .handle(ServiceEventListener.ServiceEvent.createResetEvent(ReqRunServiceContributor::class.java))
            }, ModalityState.any())
        }
        return removed
    }

    fun removeExecution(id: java.util.UUID): Boolean {
        val removed = synchronized(executions) {
            executions.removeIf { it.id == id }
        }
        if (removed) {
            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed) return@invokeLater
                project.messageBus.syncPublisher(ServiceEventListener.TOPIC)
                    .handle(ServiceEventListener.ServiceEvent.createResetEvent(ReqRunServiceContributor::class.java))
            }, ModalityState.any())
        }
        return removed
    }
}
