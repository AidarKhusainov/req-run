package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.testutil.clearReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.collectReqRunNotifications
import com.github.aidarkhusainov.reqrun.testutil.createActionEvent
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.StringSelection

class PasteCurlActionTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            clearReqRunNotifications(project)
        } finally {
            super.tearDown()
        }
    }

    fun testSelectionOverridesClipboard() {
        val curlText = "curl https://example.com"
        myFixture.configureByText("test.http", curlText)
        val action = PasteCurlAction()
        CopyPasteManager.getInstance().setContents(StringSelection("not curl"))
        myFixture.editor.selectionModel.setSelection(0, curlText.length)

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        assertEquals("GET https://example.com", myFixture.editor.document.text)
        assertEquals(1, collectReqRunNotifications(project).size)
    }

    fun testWarnsOnEmptyClipboard() {
        myFixture.configureByText("test.http", "")
        val action = PasteCurlAction()
        CopyPasteManager.getInstance().setContents(StringSelection(""))

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("Clipboard is empty", notifications.single().content)
    }

    fun testErrorsOnInvalidCurl() {
        myFixture.configureByText("test.http", "")
        val action = PasteCurlAction()
        CopyPasteManager.getInstance().setContents(StringSelection("not curl"))

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val notifications = collectReqRunNotifications(project)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.ERROR, notifications.single().type)
        assertEquals("Cannot parse cURL command.", notifications.single().content)
    }

    fun testInsertsAtCaretWhenNoSelection() {
        val curl = "curl https://example.com"
        myFixture.configureByText("test.http", "before\nafter")
        val action = PasteCurlAction()
        CopyPasteManager.getInstance().setContents(StringSelection(curl))
        val insertOffset = myFixture.editor.document.text.indexOf("after")
        myFixture.editor.caretModel.moveToOffset(insertOffset)

        clearReqRunNotifications(project)
        action.actionPerformed(createActionEvent(project, myFixture.editor, myFixture.file.virtualFile))

        val expected = "before\nGET https://example.comafter"
        assertEquals(expected, myFixture.editor.document.text)
    }
}
