package com.github.aidarkhusainov.reqrun.actions

import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InsertHttpRequestTemplateActionTest : BasePlatformTestCase() {
    fun testBuildTemplateForGetOmitsBody() {
        val action = InsertHttpRequestTemplateAction("GET")
        val method = InsertHttpRequestTemplateAction::class.java.getDeclaredMethod("buildTemplate", String::class.java)
        method.isAccessible = true

        val template = method.invoke(action, "GET") as String

        assertTrue(template.contains("GET https://example.com"))
        assertTrue(template.contains("Accept: application/json"))
        assertFalse(template.contains("Content-Type: application/json"))
        assertFalse(template.contains("\"example\": true"))
    }

    fun testBuildTemplateForPostIncludesBody() {
        val action = InsertHttpRequestTemplateAction("POST")
        val method = InsertHttpRequestTemplateAction::class.java.getDeclaredMethod("buildTemplate", String::class.java)
        method.isAccessible = true

        val template = method.invoke(action, "POST") as String

        assertTrue(template.contains("POST https://example.com"))
        assertTrue(template.contains("Content-Type: application/json"))
        assertTrue(template.contains("\"example\": true"))
    }

    fun testFindInsertionOffsetSkipsVariablePrelude() {
        val action = InsertHttpRequestTemplateAction("GET")
        val method = InsertHttpRequestTemplateAction::class.java.getDeclaredMethod(
            "findInsertionOffset",
            com.intellij.openapi.editor.Document::class.java
        )
        method.isAccessible = true

        val text = """
            @token = abc

            @baseUrl = https://example.com

            GET https://example.com
        """.trimIndent()
        val document = EditorFactory.getInstance().createDocument(text)

        val offset = method.invoke(action, document) as Int

        assertEquals(document.getLineStartOffset(4), offset)
    }
}
