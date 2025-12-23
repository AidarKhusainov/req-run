package com.github.aidarkhusainov.reqrun.ui

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.github.aidarkhusainov.reqrun.services.ReqRunExecution
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ResponseViewerTest : BasePlatformTestCase() {
    fun testCombinedTextIncludesFoldSections() {
        val execution = ReqRunExecution(
            request = HttpRequestSpec(
                method = "POST",
                url = "https://example.com",
                headers = mapOf("X-Req-1" to "a", "X-Req-2" to "b"),
                body = "req-body"
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
        assertTrue(combined.folds.any { it.startsWith("Request headers (2 lines)") })
        assertTrue(combined.folds.any { it.startsWith("Request body (") })
        assertTrue(combined.folds.any { it.startsWith("Headers (1 lines)") })
        assertTrue(combined.folds.any { it.startsWith("Body (") })
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
        val folds: List<String>
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
            placeholderField.get(fold) as String
        }
        return CombinedData(text, folds)
    }
}
