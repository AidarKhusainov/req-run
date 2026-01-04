package com.github.aidarkhusainov.reqrun.integration

import com.github.aidarkhusainov.reqrun.core.ReqRunExecutor
import com.github.aidarkhusainov.reqrun.model.BodyPart
import com.github.aidarkhusainov.reqrun.model.CompositeBody
import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.RequestBodySpec
import com.github.aidarkhusainov.reqrun.model.TextBody
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.file.Files

class ReqRunExecutorIntegrationTest : BasePlatformTestCase() {
    private lateinit var server: MockWebServer
    private lateinit var executor: ReqRunExecutor

    override fun setUp() {
        super.setUp()
        server = MockWebServer()
        server.start()
        executor = project.getService(ReqRunExecutor::class.java)
    }

    override fun tearDown() {
        try {
            server.shutdown()
        } finally {
            super.tearDown()
        }
    }

    fun testExecuteSendsGetWithoutBody() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .addHeader("Content-Type", "text/plain"),
        )

        val request =
            HttpRequestSpec(
                method = "GET",
                url = server.url("/ping").toString(),
                headers = mapOf("X-Test" to "1"),
                body = null as RequestBodySpec?,
            )

        val response = executor.execute(request)
        val recorded = server.takeRequest()

        assertEquals("GET", recorded.method)
        assertEquals("/ping", recorded.path)
        assertEquals("1", recorded.getHeader("X-Test"))
        assertEquals("", recorded.body.readUtf8())
        assertEquals("HTTP/1.1 200 OK", response.statusLine)
        assertEquals("ok", response.body)
        assertNotNull(response.headers["Content-Type"])
        assertTrue(response.durationMillis >= 0)
    }

    fun testExecuteSendsPostWithBodyAndHeaders() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("created"),
        )

        val request =
            HttpRequestSpec(
                method = "POST",
                url = server.url("/items").toString(),
                headers = mapOf("Content-Type" to "application/json"),
                body = TextBody("{\"a\":1}"),
            )

        val response = executor.execute(request)
        val recorded = server.takeRequest()

        assertEquals("POST", recorded.method)
        assertEquals("/items", recorded.path)
        assertEquals("application/json", recorded.getHeader("Content-Type"))
        assertEquals("{\"a\":1}", recorded.body.readUtf8())
        assertEquals("HTTP/1.1 201 Created", response.statusLine)
        assertEquals("created", response.body)
    }

    fun testExecuteSendsMultipartBodyWithFilePart() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok"),
        )

        val tempFile = Files.createTempFile("reqrun", ".txt")
        Files.writeString(tempFile, "file-content")
        try {
            val body =
                CompositeBody(
                    preview = "< $tempFile",
                    parts =
                        listOf(
                            BodyPart.Text(
                                "--Boundary\n" +
                                    "Content-Disposition: form-data; name=\"file\"; filename=\"file.txt\"\n\n",
                            ),
                            BodyPart.File(tempFile),
                            BodyPart.Text("\n--Boundary--"),
                        ),
                )
            val request =
                HttpRequestSpec(
                    method = "POST",
                    url = server.url("/upload").toString(),
                    headers = mapOf("Content-Type" to "multipart/form-data; boundary=Boundary"),
                    body = body,
                )

            executor.execute(request)
            val recorded = server.takeRequest()
            val expectedBody =
                "--Boundary\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"file.txt\"\n\n" +
                    "file-content\n--Boundary--"

            assertEquals(expectedBody, recorded.body.readUtf8())
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    fun testExecuteFollowsRedirects() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/final"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("done"),
        )

        val request =
            HttpRequestSpec(
                method = "GET",
                url = server.url("/start").toString(),
                headers = emptyMap<String, String>(),
                body = null as RequestBodySpec?,
            )

        val response = executor.execute(request)

        assertEquals("HTTP/1.1 200 OK", response.statusLine)
        assertEquals("done", response.body)
        assertEquals(2, server.requestCount)
    }

    fun testExecuteKeepsUnknownReasonPhraseEmpty() {
        server.enqueue(
            MockResponse()
                .setResponseCode(418)
                .setBody("teapot"),
        )

        val request =
            HttpRequestSpec(
                method = "GET",
                url = server.url("/teapot").toString(),
                headers = emptyMap<String, String>(),
                body = null as RequestBodySpec?,
            )

        val response = executor.execute(request)

        assertEquals("HTTP/1.1 418 ", response.statusLine)
        assertEquals("teapot", response.body)
    }

    fun testExecuteUsesMappedReasonPhrase() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("missing"),
        )

        val request =
            HttpRequestSpec(
                method = "GET",
                url = server.url("/missing").toString(),
                headers = emptyMap<String, String>(),
                body = null as RequestBodySpec?,
            )

        val response = executor.execute(request)

        assertEquals("HTTP/1.1 404 Not Found", response.statusLine)
        assertEquals("missing", response.body)
    }

    fun testExecuteRespectsCancellation() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok"),
        )

        val request =
            HttpRequestSpec(
                method = "GET",
                url = server.url("/slow").toString(),
                headers = emptyMap<String, String>(),
                body = null as RequestBodySpec?,
            )
        val indicator = ProgressIndicatorBase()
        indicator.cancel()

        assertThrows(ProcessCanceledException::class.java) {
            executor.execute(request, indicator)
        }
    }

    fun testExecuteHandlesEmptyBodyResponse() {
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .setBody(""),
        )

        val request =
            HttpRequestSpec(
                method = "GET",
                url = server.url("/empty").toString(),
                headers = emptyMap<String, String>(),
                body = null as RequestBodySpec?,
            )

        val response = executor.execute(request)

        assertEquals("HTTP/1.1 204 No Content", response.statusLine)
        assertEquals("", response.body)
    }
}
