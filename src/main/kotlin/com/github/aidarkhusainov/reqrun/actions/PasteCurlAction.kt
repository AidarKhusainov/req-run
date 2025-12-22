package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.core.CurlConverter
import com.github.aidarkhusainov.reqrun.lang.ReqRunFileType
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import java.awt.datatransfer.DataFlavor

class PasteCurlAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

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

        val selection = editor.selectionModel
        val selectedText = selection.selectedText?.takeIf { it.isNotBlank() }
        val clipboardText = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        val source = selectedText ?: clipboardText
        if (source.isNullOrBlank()) {
            ReqRunNotifier.warn(project, "Clipboard is empty")
            return
        }

        val spec = CurlConverter.fromCurl(source)
        if (spec == null) {
            ReqRunNotifier.error(project, "Cannot parse cURL command.")
            return
        }

        val httpText = CurlConverter.toHttp(spec)
        WriteCommandAction.runWriteCommandAction(project) {
            if (selection.hasSelection()) {
                editor.document.replaceString(selection.selectionStart, selection.selectionEnd, httpText)
                selection.removeSelection()
            } else {
                editor.document.insertString(editor.caretModel.offset, httpText)
            }
        }
        ReqRunNotifier.info(project, "Inserted HTTP request from cURL")
    }
}
