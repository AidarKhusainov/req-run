package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.core.CurlConverter
import com.github.aidarkhusainov.reqrun.core.HttpRequestParser
import com.github.aidarkhusainov.reqrun.core.RequestExtractor
import com.github.aidarkhusainov.reqrun.lang.ReqRunFileType
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import java.awt.datatransfer.StringSelection

class CopyCurlAction : AnAction(), DumbAware {
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
            ReqRunNotifier.warn(project, "Place the caret inside a request block or select text to copy.")
            return
        }

        val spec = HttpRequestParser.parse(rawRequest)
        if (spec == null) {
            ReqRunNotifier.error(project, "Cannot parse request. Use 'METHOD URL' followed by optional headers and body.")
            return
        }

        val curl = CurlConverter.toCurl(spec)
        CopyPasteManager.getInstance().setContents(StringSelection(curl))
        ReqRunNotifier.info(project, "Copied request as cURL")
    }
}
