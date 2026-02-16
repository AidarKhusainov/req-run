package com.github.aidarkhusainov.reqrun.uitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class ReqRunUiSettingsTest : ReqRunUiTestBase() {
    @Test
    fun testSettingsOverridesAndShortenHistoryUrls() {
        UiTestProject.writeAltEnv(baseUrl, altFormat = "json", altError = "error500")
        UiTestProject.writeAltPrivateEnv()
        Files.copy(UiTestProject.altSharedEnvFile, UiTestProject.sharedEnvFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.copy(UiTestProject.altPrivateEnvFile, UiTestProject.privateEnvFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        robot.openFile("requests.http")

        robot.selectEnvironment("stage")

        robot.selectLines(18, 20)
        robot.invokeAction("Run Selected Requests")
        val request = takeRequest()
        assertNotNull(request.path)
        assertEquals("stage", request.getHeader("X-Env"))
        assertEquals("42", request.getHeader("X-User"))
        assertEquals("Bearer stage-private-token", request.getHeader("Authorization"))

        UiTestProject.reset(baseUrl)
    }

    @Test
    fun testClearHistoryAndRerun() {
        robot.goToLine(19)
        robot.runRequestAtCaret()
        takeRequest()
        robot.goToLine(19)
        robot.runRequestAtCaret()
        takeRequest()
        robot.clearHistoryIfPresent()
    }

    @Test
    fun testLargeResponseKeepsUiResponsive() {
        UiTestProject.writeSharedEnv(baseUrl, devFormat = "large", devError = "error500", prodFormat = "html", prodError = "error500")
        robot.selectEnvironment("dev")
        robot.selectLines(18, 20)
        robot.invokeAction("Run Selected Requests")
        val request = takeRequest()
        assertNotNull(request.path)
        robot.openFile("notes.txt")
        assertTrue(Files.exists(UiTestProject.notesFile))
    }

    @Test
    fun testMissingEnvFilesHandled() {
        Files.deleteIfExists(UiTestProject.sharedEnvFile)
        Files.deleteIfExists(UiTestProject.privateEnvFile)

        robot.openSharedEnvFile()
        waitFor("Shared env file created") { Files.exists(UiTestProject.sharedEnvFile) }
        assertTrue(Files.readString(UiTestProject.sharedEnvFile, StandardCharsets.UTF_8).contains("{"))

        robot.openPrivateEnvFile()
        waitFor("Private env file created") { Files.exists(UiTestProject.privateEnvFile) }
        assertTrue(Files.readString(UiTestProject.privateEnvFile, StandardCharsets.UTF_8).contains("{"))
        UiTestProject.reset(baseUrl)
    }

    @Test
    fun testNetworkErrorNotification() {
        UiTestProject.writeSharedEnv(baseUrl, devFormat = "json", devError = "disconnect", prodFormat = "html", prodError = "error500")
        robot.selectEnvironment("dev")
        robot.goToLine(22)
        robot.runRequestAtCaret()
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
    }

    @Test
    fun testUnresolvedAndInvalidRequestWarnings() {
        val original = Files.readString(UiTestProject.requestsFile, StandardCharsets.UTF_8)
        val withMissing =
            original.replace(
                "X-Note: {{note}}",
                "X-Note: {{note}}\nX-Missing: {{missingVar}}",
            )
        Files.writeString(UiTestProject.requestsFile, withMissing, StandardCharsets.UTF_8)
        robot.openFile("requests.http")
        robot.goToLine(5)
        robot.runRequestAtCaret()
        val unresolvedRequest = server.takeRequest(2, TimeUnit.SECONDS)
        if (unresolvedRequest != null) {
            assertNotNull(unresolvedRequest.path)
        }

        val invalid =
            original.replace(
                "GET {{baseUrl}}/{{errorPath}}",
                "INVALID",
            )
        Files.writeString(UiTestProject.requestsFile, invalid, StandardCharsets.UTF_8)
        robot.openFile("requests.http")
        robot.goToLine(22)
        robot.runRequestAtCaret()
        server.takeRequest(2, TimeUnit.SECONDS)

        UiTestProject.reset(baseUrl)
    }
}
