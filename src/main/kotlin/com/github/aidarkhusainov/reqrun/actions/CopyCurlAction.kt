package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.core.CurlConverter
import com.github.aidarkhusainov.reqrun.core.HttpRequestParser
import com.github.aidarkhusainov.reqrun.core.RequestExtractor
import com.github.aidarkhusainov.reqrun.core.StaticAuthTokenResolver
import com.github.aidarkhusainov.reqrun.core.VariableResolver
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import java.awt.datatransfer.StringSelection
import java.nio.file.Path

class CopyCurlAction : AnAction("Copy as cURL"), DumbAware {
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
            ReqRunNotifier.warn(project, "Place the caret inside a request block or select text to copy.")
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
        val baseDir = file?.path?.let { Path.of(it).parent }
        val spec = HttpRequestParser.parse(resolvedRequest, baseDir)
        if (spec == null) {
            ReqRunNotifier.error(project, "Cannot parse request. Use 'METHOD URL' followed by optional headers and body.")
            return
        }

        val curl = CurlConverter.toCurl(spec)
        CopyPasteManager.getInstance().setContents(StringSelection(curl))
        ReqRunNotifier.info(project, "Copied request as cURL")
    }
}
