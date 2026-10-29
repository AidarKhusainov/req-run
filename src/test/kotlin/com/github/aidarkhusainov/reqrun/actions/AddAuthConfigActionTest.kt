package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService
import com.github.aidarkhusainov.reqrun.testutil.createActionEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class AddAuthConfigActionTest : BasePlatformTestCase() {
    fun testAddAuthConfigCreatesSecurityAuth() {
        val action = AddAuthConfigAction(false)
        val method =
            AddAuthConfigAction::class.java.getDeclaredMethod(
                "addAuthConfig",
                String::class.java,
                String::class.java,
            )
        method.isAccessible = true

        val result = method.invoke(action, "{}", "local")
        val textField = result.javaClass.getDeclaredField("text")
        textField.isAccessible = true
        val updatedText = textField.get(result) as String

        val json = JsonParser.parseString(updatedText).asJsonObject
        val env = json.getAsJsonObject("local")
        val security = env.getAsJsonObject("Security")
        val auth = security.getAsJsonObject("Auth")
        val config = auth.getAsJsonObject("auth")
        assertEquals("Static", config.get("Type").asString)
        assertEquals("Bearer", config.get("Scheme").asString)
        assertEquals("{{token}}", config.get("Token").asString)
    }

    fun testActionUpdatesDocumentAndNavigatesToNewAuthConfig() {
        val requestFile = myFixture.configureByText("request.http", "GET https://example.com").virtualFile
        val environmentService = project.getService(ReqRunEnvironmentService::class.java)
        environmentService.setSelectedEnvironment(null)

        AddAuthConfigAction(false).actionPerformed(createActionEvent(project, myFixture.editor, requestFile))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val envPath = Path.of(project.basePath!!, "http-client.env.json")
        val envFile = LocalFileSystem.getInstance().findFileByNioFile(envPath)
        assertNotNull(envFile)
        val document = FileDocumentManager.getInstance().getDocument(envFile!!)!!
        val environment = JsonParser.parseString(document.text).asJsonObject.getAsJsonObject("local")
        val security = environment.getAsJsonObject("Security")
        val auth = security.getAsJsonObject("Auth").getAsJsonObject("auth")
        assertEquals("Static", auth.get("Type").asString)

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        assertNotNull(editor)
        assertSame(document, editor!!.document)
        assertEquals(document.text.indexOf("\"auth\"") + 1, editor.caretModel.offset)
        assertEquals("local", environmentService.getSelectedEnvironment())
    }

    fun testNextKeySkipsExistingNames() {
        val action = AddAuthConfigAction(false)
        val method =
            AddAuthConfigAction::class.java.getDeclaredMethod(
                "nextKey",
                JsonObject::class.java,
                String::class.java,
            )
        method.isAccessible = true

        val auth =
            JsonObject().apply {
                addProperty("auth", "value")
                addProperty("auth1", "value")
            }

        val key = method.invoke(action, auth, "auth") as String

        assertEquals("auth2", key)
    }
}
