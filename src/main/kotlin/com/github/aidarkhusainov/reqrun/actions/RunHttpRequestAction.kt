package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.core.HttpRequestParser
import com.github.aidarkhusainov.reqrun.core.RequestExtractor
import com.github.aidarkhusainov.reqrun.core.StaticAuthTokenResolver
import com.github.aidarkhusainov.reqrun.core.VariableResolver
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService
import com.github.aidarkhusainov.reqrun.services.ReqRunRequestSource
import com.github.aidarkhusainov.reqrun.services.ReqRunRunner
import com.github.aidarkhusainov.reqrun.services.ReqRunServiceContributor
import com.intellij.execution.services.ServiceViewManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
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

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val visible = project != null && editor != null && file.isReqRunHttpFile()
        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (!file.isReqRunHttpFile()) {
            ReqRunNotifier.warn(project, "ReqRun works with .http files")
            return
        }

        val rawRequest = RequestExtractor.extractFull(editor)
        if (rawRequest.isNullOrBlank()) {
            ReqRunNotifier.warn(project, "Place the caret inside a request block or select text to run.")
            return
        }

        val envService = project.getService(ReqRunEnvironmentService::class.java)
        val envVariables = envService.loadVariablesForFile(file)
        val authConfigs = envService.loadAuthConfigsForFile(file)
        val fileVariables = VariableResolver.collectFileVariables(editor.document.text)
        val authResolver = StaticAuthTokenResolver.createResolver(authConfigs)
        val resolvedRequest = VariableResolver.resolveRequest(rawRequest, fileVariables, envVariables, authResolver)
        val unresolved = VariableResolver.findUnresolvedPlaceholders(resolvedRequest)
        if (unresolved.isNotEmpty()) {
            val authTokenIds = unresolved.mapNotNull { VariableResolver.extractAuthTokenId(it) }.toSet()
            val authHeaderIds = unresolved.mapNotNull { VariableResolver.extractAuthHeaderId(it) }.toSet()
            val authIds = authTokenIds + authHeaderIds
            val missingAuth = authIds.filterNot { authConfigs.containsKey(it) }.toSet()
            val authPlaceholders = unresolved.filter {
                VariableResolver.extractAuthTokenId(it) != null || VariableResolver.extractAuthHeaderId(it) != null
            }.toSet()
            val unresolvedVars = unresolved - authPlaceholders
            val builtins = VariableResolver.builtins()
            val authIssues = authIds
                .filterNot { missingAuth.contains(it) }
                .mapNotNull { StaticAuthTokenResolver.describeAuthIssue(it, authConfigs, envVariables + fileVariables, builtins) }
                .distinct()
            val messages = mutableListOf<String>()
            if (missingAuth.isNotEmpty()) {
                val label = if (missingAuth.size == 1) "Missing auth config: " else "Missing auth configs: "
                messages += label + missingAuth.sorted().joinToString(", ")
            }
            if (authIssues.isNotEmpty()) {
                val message = if (authIssues.size == 1) {
                    authIssues.single()
                } else {
                    "Auth config issues: " + authIssues.sorted().joinToString("; ")
                }
                messages += message
            }
            if (unresolvedVars.isNotEmpty()) {
                val formatted = VariableResolver.formatUnresolved(unresolvedVars)
                messages += "Unresolved variables: $formatted"
            }
            ReqRunNotifier.warn(project, messages.joinToString(" "))
            return
        }
        val spec = HttpRequestParser.parse(resolvedRequest)
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
                val runner = project.getService(ReqRunRunner::class.java)
                try {
                    val result = runner.run(spec, source, indicator)
                    when (result.status) {
                        ReqRunRunner.Status.SUCCESS -> log.info(
                            "ReqRun: completed ${spec.method} ${spec.url} -> ${result.execution.response?.statusLine}"
                        )
                        ReqRunRunner.Status.ERROR -> log.warn(
                            "ReqRun: failed ${spec.method} ${spec.url}: ${result.execution.error ?: "unknown error"}"
                        )
                    }
                    ApplicationManager.getApplication().invokeLater({
                        if (project.isDisposed) return@invokeLater
                        ServiceViewManager.getInstance(project)
                            .select(result.execution, ReqRunServiceContributor::class.java, true, true)
                        if (result.status == ReqRunRunner.Status.ERROR) {
                            ReqRunNotifier.error(project, "Request failed: ${result.execution.error ?: "unknown error"}")
                        }
                    }, ModalityState.any())
                } catch (t: ProcessCanceledException) {
                    val exec = runner.addCancelledExecution(spec, source)
                    log.info("ReqRun: cancelled ${spec.method} ${spec.url}")
                    ApplicationManager.getApplication().invokeLater({
                        if (project.isDisposed) return@invokeLater
                        ServiceViewManager.getInstance(project)
                            .select(exec, ReqRunServiceContributor::class.java, true, true)
                        ReqRunNotifier.info(project, "Request cancelled")
                    }, ModalityState.any())
                    throw t
                }
            }
        })
    }
}
