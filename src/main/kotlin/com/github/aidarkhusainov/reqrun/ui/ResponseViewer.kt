package com.github.aidarkhusainov.reqrun.ui

import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.github.aidarkhusainov.reqrun.services.ReqRunExecution
import com.github.aidarkhusainov.reqrun.settings.ReqRunResponseViewSettings
import com.github.aidarkhusainov.reqrun.settings.ResponseViewMode
import com.github.aidarkhusainov.reqrun.settings.ResponseViewMode.*
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.nio.charset.StandardCharsets
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

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
    companion object {
        private const val SOFT_WRAP_MAX_LENGTH = 20_000
    }

    private val viewSettings = ApplicationManager.getApplication().getService(ReqRunResponseViewSettings::class.java)
    private val foldState: MutableMap<Int, Boolean> = mutableMapOf()
    private val combinedField: ReadOnlyEditorField
    private val scrollPane: JBScrollPane
    val component: JComponent
    private var viewMode: ResponseViewMode = viewSettings.state.viewMode

    init {
        val combined = buildCombinedText()
        combinedField = ReadOnlyEditorField(
            project,
            combined.text,
            PlainTextFileType.INSTANCE,
            combined.folds,
            foldState,
            viewSettings.state.showLineNumbers
        )
        installPopup(combinedField) { combinedField.text }
        scrollPane = JBScrollPane(combinedField)
        component = buildContainer()
    }

    fun isSoftWrapsEnabled(): Boolean = combinedField.isSoftWrapsEnabled()

    fun setSoftWrapsEnabled(enabled: Boolean) {
        combinedField.setSoftWrapsEnabled(enabled)
    }

    fun isLineNumbersShown(): Boolean = combinedField.isLineNumbersShown()

    fun setLineNumbersShown(enabled: Boolean) {
        combinedField.setLineNumbersShown(enabled)
    }

    fun getViewMode(): ResponseViewMode = viewMode

    fun setViewMode(mode: ResponseViewMode) {
        viewMode = mode
        refreshContent()
    }

    fun scrollToTop() {
        scrollPane.verticalScrollBar.value = 0
    }

    fun scrollToEnd() {
        scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
    }

    fun copyResponseBodyToClipboard() {
        val body = responseBodyForCopy()
        CopyPasteManager.getInstance().setContents(StringSelection(body))
    }

    fun refreshContent() {
        val combined = buildCombinedText()
        foldState.clear()
        combinedField.setContent(combined.text, PlainTextFileType.INSTANCE, combined.folds)
    }

    private fun buildContainer(): JComponent {
        val group = DefaultActionGroup(
            ResponseViewSettingsActionGroup(this, viewSettings),
            ToggleSoftWrapsAction(this),
            ScrollToTopAction(this),
            ScrollToEndAction(this),
            CopyResponseBodyAction(this),
        )
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("ReqRunResponseViewer.Toolbar", group, false)
        toolbar.targetComponent = combinedField
        toolbar.component.isOpaque = false
        toolbar.component.background = UIUtil.getPanelBackground()
        toolbar.component.border = JBUI.Borders.empty(6, 0, 6, 6)
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            add(scrollPane, BorderLayout.CENTER)
            add(toolbar.component, BorderLayout.EAST)
        }
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
        val responseHeaders = normalizeText(headersText(response))
        val requestHeadersText = normalizeText(
            execution.request.headers.entries
                .joinToString("\n") { (k, v) -> "$k: $v" }
        )
        val rawBody = response?.body.orEmpty()
        val displayBody = renderBody(response, error)
        val normalizedBody = normalizeText(displayBody)
        val requestBody = execution.request.body?.takeIf { it.isNotBlank() } ?: ""
        val normalizedRequestBody = normalizeText(requestBody)
        val status = statusText(response, error)
        val bytesSource = if (response != null) rawBody else displayBody
        val bodyBytes = bytesSource.toByteArray(StandardCharsets.UTF_8).size
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
        if (viewSettings.state.showRequestMethod) {
            val requestLine = "${execution.request.method} ${execution.request.url}"
            sb.append(requestLine).append('\n')
        }
        if (requestHeadersText.isNotBlank()) {
            val reqStart = sb.length
            sb.append(requestHeadersText)
            val reqEnd = sb.length
            folds.add(
                FoldSection(
                    reqStart,
                    reqEnd,
                    "Request headers (${requestHeadersText.count { it == '\n' } + 1} lines)"))
        }
        if (normalizedRequestBody.isNotBlank()) {
            if (requestHeadersText.isNotBlank()) {
                sb.append("\n\n")
            } else {
                sb.append('\n')
            }
            val reqBodyStart = sb.length
            sb.append(normalizedRequestBody)
            val reqBodyEnd = sb.length
            folds.add(FoldSection(reqBodyStart, reqBodyEnd, "Request body (${normalizedRequestBody.length} chars)"))
        }
        sb.append("\n\n")
        sb.append(status).append('\n')
        if (responseHeaders.isNotBlank()) {
            val headersStart = sb.length
            sb.append(responseHeaders)
            val headersEnd = sb.length
            val foldHeaders = viewSettings.state.foldHeadersByDefault && displayBody.isNotEmpty()
            folds.add(
                FoldSection(
                    headersStart,
                    headersEnd,
                    "Headers (${responseHeaders.count { it == '\n' } + 1} lines)",
                    defaultExpanded = !foldHeaders
                )
            )
            sb.append("\n\n")
        } else {
            sb.append('\n')
        }
        val bodyStart = sb.length
        sb.append(normalizedBody)
        val bodyEnd = sb.length
        if (normalizedBody.isNotEmpty()) {
            folds.add(FoldSection(bodyStart, bodyEnd, "Body (${normalizedBody.length} chars)", defaultExpanded = true))
        }
        sb.append("\n\n").append(meta)
        return Combined(sb.toString(), folds)
    }

    private fun renderBody(response: HttpResponsePayload?, error: String?): String {
        if (response == null) return error ?: ""
        val rawBody = response.body
        return when (resolveViewMode(response)) {
            JSON -> renderJsonBody(response, rawBody)
            XML -> response.formattedXml?.takeIf { it.isNotEmpty() } ?: rawBody
            HTML -> response.formattedHtml?.takeIf { it.isNotEmpty() } ?: rawBody
            TEXT -> rawBody
            AUTO -> rawBody
        }
    }

    private fun responseBodyForCopy(): String {
        val response = execution.response ?: return execution.error.orEmpty()
        val rawBody = response.body
        return when (resolveViewMode(response)) {
            JSON -> response.formattedBody?.takeIf { it.isNotEmpty() } ?: rawBody
            XML -> response.formattedXml?.takeIf { it.isNotEmpty() } ?: rawBody
            HTML -> response.formattedHtml?.takeIf { it.isNotEmpty() } ?: rawBody
            TEXT, AUTO -> rawBody
        }
    }

    private fun normalizeText(text: String): String = text.replace("\r\n", "\n")

    private fun renderJsonBody(response: HttpResponsePayload, rawBody: String): String {
        val jsonError = response.jsonFormatError?.takeIf { it.isNotBlank() }
        if (jsonError != null) {
            return buildString {
                append("JSON parse error: ").append(jsonError)
                if (rawBody.isNotEmpty()) {
                    append('\n').append(rawBody)
                }
            }
        }
        return response.formattedBody?.takeIf { it.isNotEmpty() } ?: rawBody
    }

    private fun resolveViewMode(response: HttpResponsePayload): ResponseViewMode {
        return when (viewMode) {
            AUTO -> detectAutoMode(response)
            else -> viewMode
        }
    }

    private fun detectAutoMode(response: HttpResponsePayload): ResponseViewMode {
        val contentType = response.headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.lowercase()
            .orEmpty()
        return when {
            contentType.contains("application/json") || contentType.contains("+json") -> JSON
            contentType.contains("application/xml") || contentType.contains("text/xml") || contentType.contains("+xml") -> XML
            contentType.contains("text/html") -> HTML
            response.formattedBody?.isNotEmpty() == true -> JSON
            else -> TEXT
        }
    }

    private class ReadOnlyEditorField(
        project: Project?,
        text: String,
        fileType: FileType,
        folds: List<FoldSection> = emptyList(),
        private val foldState: MutableMap<Int, Boolean> = mutableMapOf(),
        showLineNumbers: Boolean
    ) : EditorTextField(null, project, fileType, false, false) {
        private val normalizedLength: Int
        private var softWrapsEnabled: Boolean
        private var lineNumbersShown: Boolean
        private var editorRef: EditorEx? = null
        private var currentFolds: List<FoldSection> = folds

        init {
            setOneLineMode(false)
            val normalized = text.replace("\r\n", "\n")
            normalizedLength = normalized.length
            softWrapsEnabled = normalizedLength <= SOFT_WRAP_MAX_LENGTH
            lineNumbersShown = showLineNumbers
            val doc = EditorFactory.getInstance().createDocument(normalized)
            doc.setReadOnly(true)
            setNewDocumentAndFileType(fileType, doc)
        }

        override fun createEditor(): EditorEx {
            val editor = super.createEditor() as EditorEx
            editorRef = editor
            editor.settings.isLineNumbersShown = lineNumbersShown
            editor.settings.isFoldingOutlineShown = true
            editor.settings.isAdditionalPageAtBottom = false
            editor.settings.isUseSoftWraps = softWrapsEnabled
            editor.isViewer = true
            val scrollPane = editor.scrollPane
            scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            applyFolds(editor, currentFolds)
            SwingUtilities.invokeLater {
                scrollPane.viewport.revalidate()
                scrollPane.viewport.repaint()
                scrollPane.revalidate()
                scrollPane.repaint()
            }
            return editor
        }

        fun isLineNumbersShown(): Boolean = lineNumbersShown

        fun setLineNumbersShown(enabled: Boolean) {
            lineNumbersShown = enabled
            val editor = editorRef ?: return
            editor.settings.isLineNumbersShown = enabled
            editor.component.revalidate()
            editor.component.repaint()
        }

        fun isSoftWrapsEnabled(): Boolean = softWrapsEnabled

        fun setSoftWrapsEnabled(enabled: Boolean) {
            softWrapsEnabled = enabled
            val editor = editorRef ?: return
            editor.settings.isUseSoftWraps = enabled
            editor.component.revalidate()
            editor.component.repaint()
        }

        fun setContent(text: String, fileType: FileType, sections: List<FoldSection>) {
            val normalized = text.replace("\r\n", "\n")
            currentFolds = sections
            val doc = EditorFactory.getInstance().createDocument(normalized)
            doc.setReadOnly(true)
            setNewDocumentAndFileType(fileType, doc)
            val editor = editorRef ?: return
            applyFolds(editor, currentFolds)
        }

        override fun removeNotify() {
            editor?.foldingModel?.allFoldRegions?.forEach { region ->
                foldState[region.startOffset] = region.isExpanded
            }
            super.removeNotify()
        }

        private fun applyFolds(editor: EditorEx, sections: List<FoldSection>) {
            ApplicationManager.getApplication().runReadAction {
                editor.foldingModel.runBatchFoldingOperation {
                    editor.foldingModel.allFoldRegions.forEach { editor.foldingModel.removeFoldRegion(it) }
                    if (sections.isEmpty()) return@runBatchFoldingOperation
                    sections.forEach { section ->
                        if (section.end > section.start) {
                            val region =
                                editor.foldingModel.addFoldRegion(section.start, section.end, section.placeholder)
                            region?.isExpanded = foldState[section.start] ?: section.defaultExpanded
                        }
                    }
                }
            }
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
