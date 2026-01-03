package com.github.aidarkhusainov.reqrun.ui

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.github.aidarkhusainov.reqrun.model.TextBody
import com.github.aidarkhusainov.reqrun.services.ReqRunExecution
import com.github.aidarkhusainov.reqrun.settings.ReqRunResponseViewSettings
import com.github.aidarkhusainov.reqrun.settings.ResponseViewMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ResponseViewerTest : BasePlatformTestCase() {
    private lateinit var settings: ReqRunResponseViewSettings
    private lateinit var savedState: ReqRunResponseViewSettings.State

    override fun setUp() {
        super.setUp()
        settings = ApplicationManager.getApplication().getService(ReqRunResponseViewSettings::class.java)
        savedState = ReqRunResponseViewSettings.State(
            showLineNumbers = settings.state.showLineNumbers,
            showRequestMethod = settings.state.showRequestMethod,
            foldHeadersByDefault = settings.state.foldHeadersByDefault,
            viewMode = settings.state.viewMode
        )
    }

    override fun tearDown() {
        settings.state.showLineNumbers = savedState.showLineNumbers
        settings.state.showRequestMethod = savedState.showRequestMethod
        settings.state.foldHeadersByDefault = savedState.foldHeadersByDefault
        settings.state.viewMode = savedState.viewMode
        super.tearDown()
    }

    fun testCombinedTextIncludesFoldSections() {
        val execution = ReqRunExecution(
            request = HttpRequestSpec(
                method = "POST",
                url = "https://example.com",
                headers = mapOf("X-Req-1" to "a", "X-Req-2" to "b"),
                body = TextBody("req-body")
            ),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = "resp",
                durationMillis = 5
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("POST https://example.com"))
        assertTrue(combined.text.contains("HTTP/1.1 200 OK"))
        assertTrue(combined.folds.any { it.placeholder.startsWith("Request headers (2 lines)") })
        assertTrue(combined.folds.any { it.placeholder.startsWith("Request body (") })
        assertTrue(combined.folds.any { it.placeholder.startsWith("Headers (1 lines)") })
        assertTrue(combined.folds.any { it.placeholder.startsWith("Body (") })
    }

    fun testFiltersStatusHeadersFromResponse() {
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf(
                    ":status" to listOf("200"),
                    "Status" to listOf("200"),
                    "Content-Type" to listOf("text/plain")
                ),
                body = "ok",
                durationMillis = 1
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("Content-Type: text/plain"))
        assertFalse(combined.text.contains("Status: 200"))
    }

    fun testRequestLineHiddenWhenSettingDisabled() {
        settings.state.showRequestMethod = false
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = emptyMap(),
                body = "ok",
                durationMillis = 1
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertFalse(combined.text.contains("GET https://example.com"))
        assertTrue(combined.text.contains("HTTP/1.1 200 OK"))
    }

    fun testFoldHeadersByDefaultWhenBodyPresent() {
        settings.state.foldHeadersByDefault = true
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = "body",
                durationMillis = 1
            ),
            error = null
        )

        val combined = buildCombined(execution)
        val headersFold = combined.folds.firstOrNull { it.placeholder.startsWith("Headers (") }

        assertNotNull(headersFold)
        assertFalse(headersFold!!.expanded)
    }

    fun testHeadersExpandedWhenBodyEmpty() {
        settings.state.foldHeadersByDefault = true
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 204 No Content",
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = "",
                durationMillis = 1
            ),
            error = null
        )

        val combined = buildCombined(execution)
        val headersFold = combined.folds.firstOrNull { it.placeholder.startsWith("Headers (") }

        assertNotNull(headersFold)
        assertTrue(headersFold!!.expanded)
    }

    fun testViewAsHtmlUsesFormattedHtml() {
        settings.state.viewMode = ResponseViewMode.HTML
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("text/html")),
                body = "<html><body>ok</body></html>",
                durationMillis = 1,
                formattedHtml = "<html>\n  <body>ok</body>\n</html>"
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("<html>\n  <body>ok</body>\n</html>"))
    }

    fun testViewAsXmlUsesFormattedXml() {
        settings.state.viewMode = ResponseViewMode.XML
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("application/xml")),
                body = "<root><a>1</a></root>",
                durationMillis = 1,
                formattedXml = "<root>\n  <a>1</a>\n</root>"
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("<root>\n  <a>1</a>\n</root>"))
    }

    fun testAutoUsesFormattedJsonWhenAvailable() {
        settings.state.viewMode = ResponseViewMode.AUTO
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = "{\"a\":1}",
                durationMillis = 1,
                formattedBody = "{\n  \"a\": 1\n}"
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("{\n  \"a\": 1\n}"))
    }

    fun testAutoUsesFormattedHtmlWhenContentTypeHtml() {
        settings.state.viewMode = ResponseViewMode.AUTO
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("text/html; charset=UTF-8")),
                body = "<html><body>ok</body></html>",
                durationMillis = 1,
                formattedHtml = "<html>\n  <body>ok</body>\n</html>"
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("<html>\n  <body>ok</body>\n</html>"))
    }

    fun testNormalizedLineEndingsInCombinedText() {
        val execution = ReqRunExecution(
            request = HttpRequestSpec(
                method = "POST",
                url = "https://example.com",
                headers = mapOf("X-Req-1" to "a"),
                body = TextBody("req\r\nbody")
            ),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = "resp\r\nline",
                durationMillis = 5
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertFalse(combined.text.contains("\r\n"))
    }

    fun testUsesFormattedBodyWhenProvided() {
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("application/json")),
                body = "{\"a\":1}",
                durationMillis = 1,
                formattedBody = "{\n  \"a\": 1\n}"
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("{\n  \"a\": 1\n}"))
        assertFalse(combined.text.contains("{\"a\":1}"))
    }

    fun testShowsJsonParseErrorBeforeRawBody() {
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("application/json")),
                body = "{\"a\":1",
                durationMillis = 1,
                jsonFormatError = "Expected ',' or '}'"
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("JSON parse error: Expected ',' or '}'"))
        assertTrue(combined.text.contains("{\"a\":1"))
    }

    fun testShowsSavedBodyInfoInCombinedText() {
        val savedPath = java.nio.file.Path.of("build", "out.bin").toString()
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = HttpResponsePayload(
                statusLine = "HTTP/1.1 200 OK",
                headers = mapOf("Content-Type" to listOf("application/octet-stream")),
                body = "",
                durationMillis = 1,
                savedBodyPath = savedPath,
                savedBodyAppend = true
            ),
            error = null
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("Saved response body to $savedPath (append)"))
        assertTrue(combined.text.contains("Saved to: $savedPath (append)"))
    }

    fun testShowsNoResponseMetaOnError() {
        val execution = ReqRunExecution(
            request = HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            response = null,
            error = "boom"
        )

        val combined = buildCombined(execution)

        assertTrue(combined.text.contains("boom"))
        assertTrue(combined.text.contains("No response; Content length:"))
    }

    private data class CombinedData(
        val text: String,
        val folds: List<FoldInfo>
    )

    private data class FoldInfo(
        val placeholder: String,
        val expanded: Boolean
    )

    private fun buildCombined(execution: ReqRunExecution): CombinedData {
        val viewer = ResponseViewer(project, execution)
        val method = ResponseViewer::class.java.getDeclaredMethod("buildCombinedText")
        method.isAccessible = true
        val combined = method.invoke(viewer)
        val combinedClass = combined.javaClass
        val textField = combinedClass.getDeclaredField("text")
        textField.isAccessible = true
        val foldsField = combinedClass.getDeclaredField("folds")
        foldsField.isAccessible = true
        val text = textField.get(combined) as String
        val folds = (foldsField.get(combined) as List<*>).mapNotNull { fold ->
            val placeholderField = fold?.javaClass?.getDeclaredField("placeholder") ?: return@mapNotNull null
            placeholderField.isAccessible = true
            val expandedField = fold.javaClass.getDeclaredField("defaultExpanded")
            expandedField.isAccessible = true
            FoldInfo(
                placeholder = placeholderField.get(fold) as String,
                expanded = expandedField.get(fold) as Boolean
            )
        }
        return CombinedData(text, folds)
    }
}
