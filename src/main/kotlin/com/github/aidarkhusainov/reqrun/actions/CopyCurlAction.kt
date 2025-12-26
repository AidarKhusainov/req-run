package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.core.CurlConverter
import com.github.aidarkhusainov.reqrun.core.HttpRequestParser
import com.github.aidarkhusainov.reqrun.core.RequestExtractor
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

        val envVariables = project.getService(ReqRunEnvironmentService::class.java).loadVariablesForFile(file)
        val fileVariables = VariableResolver.collectFileVariables(editor.document.text)
        val resolvedRequest = VariableResolver.resolveRequest(rawRequest, fileVariables, envVariables)
        val unresolved = VariableResolver.findUnresolvedPlaceholders(resolvedRequest)
        if (unresolved.isNotEmpty()) {
            val formatted = VariableResolver.formatUnresolved(unresolved)
            ReqRunNotifier.warn(project, "Unresolved variables: $formatted")
            return
        }
        val spec = HttpRequestParser.parse(resolvedRequest)
        if (spec == null) {
            ReqRunNotifier.error(project, "Cannot parse request. Use 'METHOD URL' followed by optional headers and body.")
            return
        }

        val curl = CurlConverter.toCurl(spec)
        CopyPasteManager.getInstance().setContents(StringSelection(curl))
        ReqRunNotifier.info(project, "Copied request as cURL")
    }
}
