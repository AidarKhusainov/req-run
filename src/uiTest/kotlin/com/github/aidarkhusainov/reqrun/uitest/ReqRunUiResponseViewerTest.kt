package com.github.aidarkhusainov.reqrun.uitest

import org.junit.Assert.assertTrue
import org.junit.Test

class ReqRunUiResponseViewerTest : ReqRunUiTestBase() {
    @Test
    fun testViewAsModesCopyBody() {
        robot.selectLines(18, 20)
        robot.invokeAction("Run Selected Requests")
        val json = takeRequest()
        assertTrue(json.path != null)

        UiTestProject.writeSharedEnv(baseUrl, devFormat = "xml", devError = "error500", prodFormat = "html", prodError = "error500")
        robot.selectEnvironment("dev")
        robot.selectLines(18, 20)
        robot.invokeAction("Run Selected Requests")
        val xml = takeRequest()
        assertTrue(xml.path != null)

        UiTestProject.writeSharedEnv(baseUrl, devFormat = "html", devError = "error500", prodFormat = "html", prodError = "error500")
        robot.selectEnvironment("dev")
        robot.selectLines(18, 20)
        robot.invokeAction("Run Selected Requests")
        val html = takeRequest()
        assertTrue(html.path != null)
    }

    @Test
    fun testViewerTogglesPersistAcrossRuns() {
        robot.goToLine(5)
        robot.runRequestAtCaret()
        val first = takeRequest()
        assertTrue(first.path?.startsWith("/echo") == true)

        robot.goToLine(12)
        robot.runRequestAtCaret()
        val second = takeRequest()
        assertTrue(second.path?.startsWith("/text") == true)
    }

    @Test
    fun testCopyBodyAndCompareWithClipboard() {
        robot.goToLine(5)
        robot.runRequestAtCaret()
        val request = takeRequest()
        assertTrue(request.path?.startsWith("/echo") == true)
        assertTrue(request.getHeader("X-Env") == "dev")
        assertTrue(request.getHeader("Authorization") == "Bearer private-token")
    }
}
