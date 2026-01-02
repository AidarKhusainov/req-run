package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.services.ReqRunRunner
import com.github.aidarkhusainov.reqrun.testutil.clearReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.collectReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.createActionEvent
import com.intellij.notification.NotificationType
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RunHttpRequestsGroupActionTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            project.getService(ReqRunRunner::class.java).setExecutorForTests(null)
            clearReqRunNotifications(project)
        } finally {
            super.tearDown()
        }
    }

    fun testUpdateTextForSelection() {
        myFixture.configureByText("test.http", "GET https://example.com")
        myFixture.editor.selectionModel.setSelection(0, 3)
        val action = RunHttpRequestsGroupAction()
        val event = createActionEvent(project, myFixture.editor, myFixture.file.virtualFile)

        action.update(event)

        assertEquals("Run Selected Requests", event.presentation.text)
    }

    fun testUpdateTextForSingleFileSelection() {
        myFixture.configureByText("test.http", "GET https://example.com")
        val action = RunHttpRequestsGroupAction()
        val files = arrayOf(myFixture.file.virtualFile)
        val event = createActionEvent(project, null, null, files)

        action.update(event)

        assertEquals("Run 'All in test.http'", event.presentation.text)
    }

    fun testWarnsWhenNoRequestsFound() {
        myFixture.configureByText("test.http", "")
        val action = RunHttpRequestsGroupAction()
        val event = createActionEvent(project, myFixture.editor, myFixture.file.virtualFile)

        clearReqRunNotifications(project)
        action.actionPerformed(event)

        val notification = waitForNotification()
        assertEquals(NotificationType.WARNING, notification.type)
        assertEquals("No HTTP requests found to run.", notification.content)
    }

    fun testErrorsOnExecutionFailures() {
        val runner = project.getService(ReqRunRunner::class.java)
        runner.setExecutorForTests { _, _ -> throw IllegalStateException("boom") }
        myFixture.configureByText("test.http", "GET https://example.com")
        val action = RunHttpRequestsGroupAction()
        val event = createActionEvent(project, myFixture.editor, myFixture.file.virtualFile)

        clearReqRunNotifications(project)
        action.actionPerformed(event)

        val notification = waitForNotification()
        assertEquals(NotificationType.ERROR, notification.type)
        assertEquals("Completed with errors: 1/1 request(s).", notification.content)
    }

    fun testWarnsOnUnresolvedVariables() {
        myFixture.configureByText("test.http", "GET {{missing}}/v1")
        val action = RunHttpRequestsGroupAction()
        val event = createActionEvent(project, myFixture.editor, myFixture.file.virtualFile)

        clearReqRunNotifications(project)
        action.actionPerformed(event)

        val notification = waitForNotification()
        assertEquals(NotificationType.WARNING, notification.type)
        assertEquals("Skipped 1 request(s) due to unresolved variables.", notification.content)
    }

    fun testWarnsOnMissingAuthConfig() {
        myFixture.configureByText(
            "test.http",
            """
                GET https://example.com
                Authorization: Bearer {{${'$'}auth.token("bearer")}}
            """.trimIndent()
        )
        val action = RunHttpRequestsGroupAction()
        val event = createActionEvent(project, myFixture.editor, myFixture.file.virtualFile)

        clearReqRunNotifications(project)
        action.actionPerformed(event)

        val notification = waitForNotificationContent("Missing auth config: bearer")
        assertEquals(NotificationType.WARNING, notification.type)
    }

    private fun waitForNotification(): com.intellij.notification.Notification {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            val notifications = collectReqRunNotifications(project)
            if (notifications.isNotEmpty()) return notifications.last()
            Thread.sleep(25)
        }
        val notifications = collectReqRunNotifications(project)
        assertTrue("Expected notification was not posted", notifications.isNotEmpty())
        return notifications.last()
    }

    private fun waitForNotificationContent(content: String): com.intellij.notification.Notification {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            val notifications = collectReqRunNotifications(project)
            val match = notifications.firstOrNull { it.content == content }
            if (match != null) return match
            Thread.sleep(25)
        }
        val notifications = collectReqRunNotifications(project)
        val match = notifications.firstOrNull { it.content == content }
        assertNotNull("Expected notification '$content' was not posted", match)
        return match!!
    }
}
