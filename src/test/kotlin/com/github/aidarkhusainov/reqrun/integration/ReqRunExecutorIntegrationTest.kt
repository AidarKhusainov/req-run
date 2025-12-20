package com.github.aidarkhusainov.reqrun.integration

import com.github.aidarkhusainov.reqrun.core.ReqRunExecutor
import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReqRunExecutorIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var executor: ReqRunExecutor

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        executor = ReqRunExecutor()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `execute sends GET without body`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .addHeader("Content-Type", "text/plain")
        )

        val request = HttpRequestSpec(
            method = "GET",
            url = server.url("/ping").toString(),
            headers = mapOf("X-Test" to "1"),
            body = null
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

    @Test
    fun `execute sends POST with body and headers`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("created")
        )

        val request = HttpRequestSpec(
            method = "POST",
            url = server.url("/items").toString(),
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"a\":1}"
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

    @Test
    fun `execute follows redirects`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/final")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("done")
        )

        val request = HttpRequestSpec(
            method = "GET",
            url = server.url("/start").toString(),
            headers = emptyMap(),
            body = null
        )

        val response = executor.execute(request)

        assertEquals("HTTP/1.1 200 OK", response.statusLine)
        assertEquals("done", response.body)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `execute keeps unknown reason phrase empty`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(418)
                .setBody("teapot")
        )

        val request = HttpRequestSpec(
            method = "GET",
            url = server.url("/teapot").toString(),
            headers = emptyMap(),
            body = null
        )

        val response = executor.execute(request)

        assertEquals("HTTP/1.1 418 ", response.statusLine)
        assertEquals("teapot", response.body)
    }

    @Test
    fun `execute handles empty body response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .setBody("")
        )

        val request = HttpRequestSpec(
            method = "GET",
            url = server.url("/empty").toString(),
            headers = emptyMap(),
            body = null
        )

        val response = executor.execute(request)

        assertEquals("HTTP/1.1 204 No Content", response.statusLine)
        assertEquals("", response.body)
    }
}
