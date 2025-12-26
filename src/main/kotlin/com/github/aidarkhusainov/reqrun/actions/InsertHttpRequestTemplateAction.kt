package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware

class InsertHttpRequestTemplateAction(private val method: String) : AnAction(method), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val template = buildTemplate(method)
        val document = editor.document
        val insertionOffset = findInsertionOffset(document)
        val insertText = if (insertionOffset < document.textLength) {
            template.trimEnd() + "\n\n###\n\n"
        } else {
            template.trimEnd() + "\n\n"
        }
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(insertionOffset, insertText)
            editor.caretModel.moveToOffset(insertionOffset + method.length + 1)
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
            editor.contentComponent.requestFocusInWindow()
        }
        ReqRunNotifier.info(project, "Inserted $method request template")
    }

    private fun buildTemplate(method: String): String {
        val needsBody = method != "GET" && method != "DELETE"
        val sb = StringBuilder()
        sb.append(method).append(" https://example.com\n")
        if (needsBody) {
            sb.append("Content-Type: application/json\n")
        }
        sb.append("Accept: application/json\n\n")
        if (needsBody) {
            sb.append("{\n  \"example\": true\n}\n")
        }
        return sb.toString()
    }

    private fun findInsertionOffset(document: com.intellij.openapi.editor.Document): Int {
        val text = document.text
        if (text.isBlank()) return 0
        val lineCount = document.lineCount
        var lastPreludeLine = -1
        for (line in 0 until lineCount) {
            val lineText = document.getText(
                com.intellij.openapi.util.TextRange(
                    document.getLineStartOffset(line),
                    document.getLineEndOffset(line)
                )
            )
            val trimmed = lineText.trim()
            if (trimmed.isEmpty() || isVariableDefinition(trimmed)) {
                lastPreludeLine = line
                continue
            }
            break
        }
        return if (lastPreludeLine + 1 < lineCount) {
            document.getLineStartOffset(lastPreludeLine + 1)
        } else {
            document.textLength
        }
    }

    private fun isVariableDefinition(line: String): Boolean {
        return line.startsWith("@") && line.contains("=")
    }
}
