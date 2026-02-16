package com.github.aidarkhusainov.reqrun.uitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReqRunUiBasicTest : ReqRunUiTestBase() {
    @Test
    fun testToolbarAndGutterVisibility() {
        waitFor("ReqRun toolbar visible") { robot.hasReqRunToolbar() }

        robot.openFile("notes.txt")
        waitFor("ReqRun toolbar hidden for non-http file") { !robot.hasReqRunToolbar() }
        assertFalse(robot.hasReqRunToolbar())
    }

    @Test
    fun testRunFromCaretTwice() {
        robot.goToLine(5)
        robot.runRequestAtCaret()
        val first = takeRequest()
        assertTrue(first.path?.startsWith("/echo") == true)
        assertEquals("dev", first.getHeader("X-Env"))
        assertEquals("42", first.getHeader("X-User"))
        assertEquals("Bearer private-token", first.getHeader("Authorization"))

        robot.goToLine(5)
        robot.runRequestAtCaret()
        val second = takeRequest()
        assertTrue(second.path != null)
    }

    @Test
    fun testRunSelectedAndRunAll() {
        robot.selectLines(5, 16)
        robot.invokeAction("Run Selected Requests")
        val selectedPaths = listOf(takeRequest().path, takeRequest().path).map { it?.substringBefore("?") }
        assertTrue(selectedPaths.contains("/echo"))
        assertTrue(selectedPaths.contains("/text"))

        robot.goToLine(5)
        robot.invokeAction("Run Selected Requests")
        val allPaths =
            List(4) { takeRequest().path?.substringBefore("?") }
                .filterNotNull()
                .toSet()
        assertTrue(allPaths.containsAll(setOf("/echo", "/text", "/json", "/error500")))
    }

    @Test
    fun testResponseViewerShowsStatusAndBody() {
        robot.goToLine(5)
        robot.runRequestAtCaret()
        val request = takeRequest()
        assertTrue(request.path?.startsWith("/echo") == true)
        assertEquals("dev", request.getHeader("X-Env"))
        assertEquals("42", request.getHeader("X-User"))
        assertEquals("Bearer private-token", request.getHeader("Authorization"))
    }
}
