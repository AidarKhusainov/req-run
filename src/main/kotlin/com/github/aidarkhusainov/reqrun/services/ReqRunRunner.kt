package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.core.ReqRunExecutor
import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.PROJECT)
class ReqRunRunner(private val project: Project) {
    enum class Status {
        SUCCESS,
        ERROR
    }

    data class RunResult(
        val execution: ReqRunExecution,
        val status: Status
    )

    @Volatile
    private var executorOverride: ((HttpRequestSpec, ProgressIndicator?) -> HttpResponsePayload)? = null

    fun run(
        spec: HttpRequestSpec,
        source: ReqRunRequestSource?,
        indicator: ProgressIndicator?
    ): RunResult {
        val executor = project.getService(ReqRunExecutor::class.java)
        val execService = project.getService(ReqRunExecutionService::class.java)
        return try {
            val response = executorOverride?.invoke(spec, indicator) ?: executor.execute(spec, indicator)
            val exec = execService.addExecution(spec, response, null, source)
            RunResult(exec, Status.SUCCESS)
        } catch (t: Throwable) {
            if (t is ProcessCanceledException) throw t
            val exec = execService.addExecution(spec, null, t.message, source)
            RunResult(exec, Status.ERROR)
        }
    }

    fun addCancelledExecution(
        spec: HttpRequestSpec,
        source: ReqRunRequestSource?
    ): ReqRunExecution {
        val execService = project.getService(ReqRunExecutionService::class.java)
        return execService.addExecution(spec, null, "Cancelled by user", source)
    }

    @TestOnly
    fun setExecutorForTests(override: ((HttpRequestSpec, ProgressIndicator?) -> HttpResponsePayload)?) {
        executorOverride = override
    }
}
