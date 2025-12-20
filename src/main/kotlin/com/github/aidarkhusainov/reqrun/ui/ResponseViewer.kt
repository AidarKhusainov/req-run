package com.github.aidarkhusainov.reqrun.ui

import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.github.aidarkhusainov.reqrun.services.ReqRunExecution
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBScrollPane
import java.awt.datatransfer.DataFlavor
import java.nio.charset.StandardCharsets
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.JComponent

private data class FoldSection(
    val start: Int,
    val end: Int,
    val placeholder: String,
    val defaultExpanded: Boolean = false
)

class ResponseViewer(
    private val project: Project,
    private val execution: ReqRunExecution
) {
    private val foldState: MutableMap<Int, Boolean> = mutableMapOf()
    val component: JComponent = buildComponent()

    private fun buildComponent(): JComponent {
        val combined = buildCombinedText()
        val combinedField = ReadOnlyEditorField(combined.text, PlainTextFileType.INSTANCE, combined.folds, foldState)
        installPopup(combinedField) { combinedField.text }
        return JBScrollPane(combinedField)
    }

    private fun statusText(response: HttpResponsePayload?, error: String?): String =
        response?.statusLine ?: (error ?: "No response")

    private fun headersText(response: HttpResponsePayload?): String =
        response
            ?.headers
            ?.entries
            ?.asSequence()
            ?.filterNot { (name, _) -> name.startsWith(":") || name.equals("status", ignoreCase = true) }
            ?.joinToString("\n") { (k, v) -> "$k: ${v.joinToString(", ")}" }
            ?: ""

    private data class Combined(
        val text: String,
        val folds: List<FoldSection>
    )

    private fun buildCombinedText(): Combined {
        val response = execution.response
        val error = execution.error
        val responseHeaders = headersText(response)
        val requestHeadersText = execution.request.headers.entries
            .joinToString("\n") { (k, v) -> "$k: $v" }
        val body = response?.body?.takeIf { it.isNotEmpty() } ?: (error ?: "")
        val status = statusText(response, error)
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size
        val code = response?.statusLine?.split(" ")?.getOrNull(1)
        val reason = response?.statusLine?.split(" ", limit = 3)?.getOrNull(2).orEmpty()
        val meta = if (response != null && code != null) {
            val reasonPart = if (reason.isNotBlank()) " ($reason)" else ""
            "Response code: $code$reasonPart; Time: ${response.durationMillis}ms; Content length: $bodyBytes bytes ($bodyBytes B)"
        } else {
            "No response; Content length: $bodyBytes bytes ($bodyBytes B)"
        }
        val folds = mutableListOf<FoldSection>()
        val sb = StringBuilder()
        val requestLine = "${execution.request.method} ${execution.request.url}"
        sb.append(requestLine).append('\n')
        if (requestHeadersText.isNotBlank()) {
            val reqStart = sb.length
            sb.append(requestHeadersText)
            val reqEnd = sb.length
            folds.add(FoldSection(reqStart, reqEnd, "Request headers (${requestHeadersText.count { it == '\n' } + 1} lines)"))
        }
        sb.append("\n\n")
        sb.append(status).append('\n')
        if (responseHeaders.isNotBlank()) {
            val headersStart = sb.length
            sb.append(responseHeaders)
            val headersEnd = sb.length
            folds.add(FoldSection(headersStart, headersEnd, "Headers (${responseHeaders.count { it == '\n' } + 1} lines)"))
            sb.append("\n\n")
        } else {
            sb.append('\n')
        }
        val bodyStart = sb.length
        sb.append(body)
        val bodyEnd = sb.length
        if (body.isNotEmpty()) {
            folds.add(FoldSection(bodyStart, bodyEnd, "Body (${body.length} chars)", defaultExpanded = true))
        }
        sb.append("\n\n").append(meta)
        return Combined(sb.toString(), folds)
    }

    private class ReadOnlyEditorField(
        text: String,
        fileType: FileType,
        private val folds: List<FoldSection> = emptyList(),
        private val foldState: MutableMap<Int, Boolean> = mutableMapOf()
    ) : EditorTextField(null, null, fileType, false, false) {

        init {
            setOneLineMode(false)
            val normalized = text.replace("\r\n", "\n")
            val doc = EditorFactory.getInstance().createDocument(normalized)
            doc.setReadOnly(true)
            setNewDocumentAndFileType(fileType, doc)
        }

        override fun createEditor(): EditorEx {
            val editor = super.createEditor() as EditorEx
            editor.settings.isLineNumbersShown = true
            editor.settings.isFoldingOutlineShown = true
            editor.settings.isAdditionalPageAtBottom = false
            editor.settings.isUseSoftWraps = true
            editor.isViewer = true
            val scrollPane = editor.scrollPane
            scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            if (folds.isNotEmpty()) {
                ApplicationManager.getApplication().runReadAction {
                    editor.foldingModel.runBatchFoldingOperation {
                        folds.forEach { section ->
                            if (section.end > section.start) {
                                val region = editor.foldingModel.addFoldRegion(section.start, section.end, section.placeholder)
                                region?.isExpanded = foldState[section.start] ?: section.defaultExpanded
                            }
                        }
                    }
                }
                SwingUtilities.invokeLater {
                    scrollPane.viewport.revalidate()
                    scrollPane.viewport.repaint()
                    scrollPane.revalidate()
                    scrollPane.repaint()
                }
            }
            return editor
        }

        override fun removeNotify() {
            editor?.foldingModel?.allFoldRegions?.forEach { region ->
                foldState[region.startOffset] = region.isExpanded
            }
            super.removeNotify()
        }
    }

    private fun installPopup(field: EditorTextField, textSupplier: () -> String) {
        val group = DefaultActionGroup(CompareWithClipboardAction(textSupplier))
        PopupHandler.installPopupMenu(field, group, ActionPlaces.EDITOR_POPUP)
    }

    private inner class CompareWithClipboardAction(
        private val textSupplier: () -> String
    ) : DumbAwareAction("Compare with Clipboard") {
        override fun actionPerformed(e: AnActionEvent) {
            val clipboardText = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
            if (clipboardText.isNullOrEmpty()) {
                ReqRunNotifier.warn(project, "Clipboard is empty")
                return
            }
            val responseText = textSupplier()
            val factory = DiffContentFactory.getInstance()
            val request = SimpleDiffRequest(
                "Compare Response with Clipboard",
                factory.create(project, responseText),
                factory.create(project, clipboardText),
                "Response",
                "Clipboard"
            )
            DiffManager.getInstance().showDiff(project, request)
        }
    }
}
