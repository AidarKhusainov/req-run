package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.core.HttpRequestParser
import com.github.aidarkhusainov.reqrun.core.ReqRunExecutor
import com.github.aidarkhusainov.reqrun.core.RequestExtractor
import com.github.aidarkhusainov.reqrun.lang.ReqRunFileType
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.github.aidarkhusainov.reqrun.services.ReqRunExecutionService
import com.github.aidarkhusainov.reqrun.services.ReqRunRequestSource
import com.github.aidarkhusainov.reqrun.services.ReqRunServiceContributor
import com.intellij.execution.services.ServiceViewManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware

class RunHttpRequestAction : AnAction(), DumbAware {
    private val log = logger<RunHttpRequestAction>()
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = project != null && editor != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (file?.extension?.equals("http", ignoreCase = true) != true && file?.fileType !is ReqRunFileType) {
            ReqRunNotifier.warn(project, "ReqRun works with .http files")
            return
        }

        val rawRequest = RequestExtractor.extractFull(editor)
        if (rawRequest.isNullOrBlank()) {
            ReqRunNotifier.warn(project, "Place the caret inside a request block or select text to run.")
            return
        }

        val spec = HttpRequestParser.parse(rawRequest)
        if (spec == null) {
            log.warn("ReqRun: failed to parse request (length=${rawRequest.length})")
            ReqRunNotifier.error(
                project,
                "Cannot parse request. Use 'METHOD URL' followed by optional headers and body."
            )
            return
        }

        val source = file?.let { ReqRunRequestSource(it, editor.caretModel.offset) }
        log.info("ReqRun: executing ${spec.method} ${spec.url}")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Executing HTTP request", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    val executor = project.getService(ReqRunExecutor::class.java)
                    val response = executor.execute(spec, indicator)
                    log.info("ReqRun: completed ${spec.method} ${spec.url} -> ${response.statusLine} (${response.durationMillis}ms)")
                    val exec = project.getService(ReqRunExecutionService::class.java)
                        .addExecution(spec, response, null, source)
                    ApplicationManager.getApplication().invokeLater({
                        ServiceViewManager.getInstance(project)
                            .select(exec, ReqRunServiceContributor::class.java, true, true)
                    }, ModalityState.any())
                } catch (t: ProcessCanceledException) {
                    log.info("ReqRun: cancelled ${spec.method} ${spec.url}")
                    val exec = project.getService(ReqRunExecutionService::class.java)
                        .addExecution(spec, null, "Cancelled by user", source)
                    ApplicationManager.getApplication().invokeLater({
                        ServiceViewManager.getInstance(project)
                            .select(exec, ReqRunServiceContributor::class.java, true, true)
                    }, ModalityState.any())
                    ReqRunNotifier.info(project, "Request cancelled")
                    throw t
                } catch (t: Throwable) {
                    log.warn("ReqRun: failed ${spec.method} ${spec.url}: ${t.message ?: "unknown error"}", t)
                    val exec = project.getService(ReqRunExecutionService::class.java)
                        .addExecution(spec, null, t.message, source)
                    ApplicationManager.getApplication().invokeLater({
                        ServiceViewManager.getInstance(project)
                            .select(exec, ReqRunServiceContributor::class.java, true, true)
                    }, ModalityState.any())
                    ReqRunNotifier.error(project, "Request failed: ${t.message ?: "unknown error"}")
                }
            }
        })
    }
}
