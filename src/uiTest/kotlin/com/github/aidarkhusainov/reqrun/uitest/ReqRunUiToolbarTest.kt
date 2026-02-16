package com.github.aidarkhusainov.reqrun.uitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ReqRunUiToolbarTest : ReqRunUiTestBase() {

    @Test
    fun testEnvSelectorAndOpenEnvFiles() {
        robot.openEnvSelector()
        waitFor("Env menu includes dev") { robot.hasTextInUi("dev") }
        waitFor("Env menu includes prod") { robot.hasTextInUi("prod") }
        UiKeyboard.pressEsc()

        robot.selectEnvironment("prod")
        robot.goToLine(5)
        robot.runRequestAtCaret()
        val request = takeRequest()
        assertEquals("prod", request.getHeader("X-Env"))

        robot.openSharedEnvFile()
        waitFor("Shared env file exists") { Files.exists(UiTestProject.sharedEnvFile) }
        robot.openPrivateEnvFile()
        waitFor("Private env file exists") { Files.exists(UiTestProject.privateEnvFile) }
        robot.openFile("requests.http")

        val updated =
            """
            {
              "stage2": {
                "baseUrl": "$baseUrl",
                "envName": "stage2",
                "format": "json",
                "errorPath": "error500",
                "Security": {
                  "Auth": {
                    "primary": {
                      "Type": "Static",
                      "Scheme": "Bearer",
                      "Token": "stage2-token"
                    }
                  }
                }
              }
            }
            """.trimIndent()
        Files.writeString(UiTestProject.sharedEnvFile, updated, StandardCharsets.UTF_8)
        robot.openEnvSelector()
        waitFor("Env menu reflects updated env file") { robot.hasTextInUi("stage2") }
        UiKeyboard.pressEsc()
    }

    @Test
    fun testTemplateInsertionAndNotification() {
        robot.insertHttpTemplate("GET")
        UiKeyboard.undo()
    }

    @Test
    fun testCopyAndPasteCurl() {
        ClipboardUtils.clear()
        robot.selectLines(5, 9)
        robot.invokeAction("Convert to cURL and Copy")

        robot.goToLine(1)
        robot.selectLines(1, 1)
        UiKeyboard.pasteText("curl --config test.conf https://example.com")
        robot.selectLines(1, 1)
        robot.invokeAction("Paste cURL as HTTP")
        UiKeyboard.selectAll()
        UiKeyboard.copy()
        assertTrue(ClipboardUtils.getText().contains("https://example.com"))
    }
}
