package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.core.CurlConverter
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class PasteCurlAction :
    AnAction("Paste cURL"),
    DumbAware {
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

        val selection = editor.selectionModel
        val selectedText = selection.selectedText?.takeIf { it.isNotBlank() }
        val clipboardText = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        val source = selectedText ?: clipboardText
        if (source.isNullOrBlank()) {
            ReqRunNotifier.warn(project, "Clipboard is empty")
            return
        }

        if (containsConfigFlag(source)) {
            ProgressManager
                .getInstance()
                .run(
                    object : Task.Backgroundable(project, "Parsing cURL", false) {
                        override fun run(indicator: ProgressIndicator) {
                            val result =
                                CurlConverter.fromCurlDetailed(source) { path ->
                                    readConfigFile(path, file)
                                }
                            ApplicationManager.getApplication().invokeLater {
                                if (project.isDisposed) return@invokeLater
                                applyResult(project, editor, selection, source, result)
                            }
                        }
                    },
                )
            return
        }

        val result = CurlConverter.fromCurlDetailed(source)
        applyResult(project, editor, selection, source, result)
    }

    private fun applyResult(
        project: com.intellij.openapi.project.Project,
        editor: com.intellij.openapi.editor.Editor,
        selection: com.intellij.openapi.editor.SelectionModel,
        source: String,
        result: CurlConverter.CurlParseResult,
    ) {
        val spec = result.spec
        if (spec == null) {
            val detail = result.error?.let { formatIssue(source, it.message, it.offset) }
            val message = detail?.let { "Cannot parse cURL command: $it" } ?: "Cannot parse cURL command."
            ReqRunNotifier.error(project, message)
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
        val warning = result.warnings.firstOrNull()
        if (warning != null) {
            val detail = formatIssue(source, warning.message, warning.offset)
            val suffix = if (result.warnings.size > 1) " (+${result.warnings.size - 1} more)" else ""
            ReqRunNotifier.warn(project, "Inserted HTTP request with warnings: $detail$suffix")
        } else {
            ReqRunNotifier.info(project, "Inserted HTTP request from cURL")
        }
    }

    private fun formatIssue(
        source: String,
        message: String,
        offset: Int,
    ): String {
        val safeOffset = offset.coerceIn(0, source.length)
        var line = 1
        var column = 1
        var i = 0
        while (i < safeOffset) {
            val ch = source[i]
            if (ch == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
            i += 1
        }
        return "$message (line $line, column $column)."
    }

    private fun containsConfigFlag(source: String): Boolean =
        Regex("(^|\\s)(-K|--config)(\\s|=)").containsMatchIn(
            source,
        )

    private fun readConfigFile(
        pathText: String,
        file: com.intellij.openapi.vfs.VirtualFile?,
    ): String? {
        val baseDir = file?.path?.let { Path.of(it).parent }
        val resolved =
            try {
                val path = Path.of(pathText)
                if (path.isAbsolute || baseDir == null) {
                    path
                } else {
                    baseDir.resolve(path)
                }
            } catch (_: InvalidPathException) {
                return null
            }
        return try {
            Files.readString(resolved)
        } catch (_: Exception) {
            null
        }
    }
}
