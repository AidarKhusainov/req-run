package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.testutil.clearReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.collectReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.createActionEvent
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor

class CopyCurlActionTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            clearReqRunNotifications(project)
        } finally {
            super.tearDown()
        }
    }

    fun testWarnsOnNonHttpFile() {
        myFixture.configureByText("test.txt", "GET https://example.com")
        val action = CopyCurlAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("ReqRun works with .http files", notifications.single().content)
    }

    fun testWarnsWhenNoRequestBlock() {
        myFixture.configureByText("test.http", "")
        val action = CopyCurlAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("Place the caret inside a request block or select text to copy.", notifications.single().content)
    }

    fun testCopiesCurlOnSuccess() {
        val text = """
            POST https://example.com
            X-Test: v

            body
        """.trimIndent()
        myFixture.configureByText("test.http", text)
        val action = CopyCurlAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val clipboardText = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertEquals("curl -X POST 'https://example.com' -H 'X-Test: v' --data 'body'", clipboardText)

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.INFORMATION, notifications.single().type)
        assertEquals("Copied request as cURL", notifications.single().content)
    }

    fun testWarnsOnUnresolvedVariables() {
        myFixture.configureByText("test.http", "GET {{missing}}/v1")
        val action = CopyCurlAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("Unresolved variables: missing", notifications.single().content)
    }

    fun testWarnsOnMissingAuthConfig() {
        myFixture.configureByText(
            "test.http",
            """
                GET https://example.com
                Authorization: Bearer {{${'$'}auth.token("bearer")}}
            """.trimIndent()
        )
        val action = CopyCurlAction()

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("Missing auth config: bearer", notifications.single().content)
    }
}
