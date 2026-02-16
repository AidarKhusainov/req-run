package com.github.aidarkhusainov.reqrun.uitest

import com.intellij.remoterobot.RemoteRobot
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import java.time.Duration
import java.util.concurrent.TimeUnit

abstract class ReqRunUiTestBase {
    protected val remoteRobot = RemoteRobot("http://127.0.0.1:${System.getProperty("robot-server.port", "8082")}")
    protected val robot = ReqRunUiRobot(remoteRobot)
    protected lateinit var server: MockWebServer
    protected lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer().apply { dispatcher = ReqRunDispatcher() }
        server.start()
        baseUrl = server.url("/").toString().removeSuffix("/")
        UiTestProject.reset(baseUrl)
        robot.openProject(UiTestProject.root())
        robot.setProjectEnvPaths(sharedPath = null, privatePath = null, useProjectPaths = false)
        robot.setShortenHistoryUrls(enabled = false)
        robot.setResponseViewSettings(
            showLineNumbers = true,
            showRequestMethod = true,
            foldHeadersByDefault = true,
        )
        robot.reloadUnsavedDocuments()
        robot.openFile("requests.http")
        waitFor("ReqRun toolbar visible", timeout = Duration.ofSeconds(60)) { robot.hasReqRunToolbar() }
        robot.selectEnvironment("dev")
        waitFor("ReqRun environment selected") { robot.hasTextInUi("dev") }
        robot.clearHistoryIfPresent()
        ClipboardUtils.clear()
    }

    @After
    fun tearDown() {
        try {
            runCatching { robot.clearHistoryIfPresent() }
            runCatching { robot.reloadUnsavedDocuments() }
        } finally {
            runCatching { server.shutdown() }
            runCatching { ClipboardUtils.clear() }
        }
    }

    protected fun takeRequest(timeoutSeconds: Long = 15): RecordedRequest =
        server.takeRequest(timeoutSeconds, TimeUnit.SECONDS)
            ?: throw AssertionError("Expected request within ${timeoutSeconds}s, but none arrived.")

    private class ReqRunDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path?.substringBefore("?").orEmpty()
            return when (path) {
                "/echo" -> {
                    val env = request.getHeader("X-Env").orEmpty()
                    val user = request.getHeader("X-User").orEmpty()
                    val auth = request.getHeader("Authorization").orEmpty()
                    val note = request.getHeader("X-Note").orEmpty()
                    val body =
                        """{"method":"${request.method}","path":"${request.path}","env":"$env","user":"$user","auth":"$auth","note":"$note"}"""
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(body)
                }
                "/text" ->
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "text/plain")
                        .setBody("ok")
                "/json" ->
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody("{\"a\":1,\"b\":2}")
                "/xml" ->
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/xml")
                        .setBody("<root><a>1</a></root>")
                "/html" ->
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "text/html")
                        .setBody("<html><body>ok</body></html>")
                "/large" ->
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "text/plain")
                        .setBodyDelay(2, TimeUnit.SECONDS)
                        .setBody("0123456789".repeat(20_000))
                "/error500" ->
                    MockResponse()
                        .setResponseCode(500)
                        .addHeader("Content-Type", "text/plain")
                        .setBody("boom")
                "/disconnect" ->
                    MockResponse()
                        .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                else ->
                    MockResponse()
                        .setResponseCode(404)
                        .setBody("not found")
            }
        }
    }
}
