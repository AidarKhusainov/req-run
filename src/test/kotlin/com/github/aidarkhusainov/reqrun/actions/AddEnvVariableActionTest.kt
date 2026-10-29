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

class AddEnvVariableActionTest : BasePlatformTestCase() {
    fun testAddVariableCreatesEnvironmentAndKey() {
        val action = AddEnvVariableAction(false)
        val method =
            AddEnvVariableAction::class.java.getDeclaredMethod(
                "addVariable",
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
        assertNotNull(env)
        assertEquals("value", env.get("newVar").asString)
    }

    fun testActionUpdatesDocumentAndNavigatesToNewVariable() {
        val requestFile = myFixture.configureByText("request.http", "GET https://example.com").virtualFile
        val environmentService = project.getService(ReqRunEnvironmentService::class.java)
        environmentService.setSelectedEnvironment(null)

        AddEnvVariableAction(false).actionPerformed(createActionEvent(project, myFixture.editor, requestFile))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val envFile = LocalFileSystem.getInstance().findFileByNioFile(Path.of(project.basePath!!, "http-client.env.json"))
        assertNotNull(envFile)
        val document = FileDocumentManager.getInstance().getDocument(envFile!!)!!
        val environment = JsonParser.parseString(document.text).asJsonObject.getAsJsonObject("local")
        assertEquals("value", environment.get("newVar").asString)

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        assertNotNull(editor)
        assertSame(document, editor!!.document)
        assertEquals(document.text.indexOf("\"newVar\"") + 1, editor.caretModel.offset)
        assertEquals("local", environmentService.getSelectedEnvironment())
    }

    fun testNextKeySkipsExistingNames() {
        val action = AddEnvVariableAction(false)
        val method =
            AddEnvVariableAction::class.java.getDeclaredMethod(
                "nextKey",
                JsonObject::class.java,
                String::class.java,
            )
        method.isAccessible = true

        val env =
            JsonObject().apply {
                addProperty("newVar", "value")
                addProperty("newVar1", "value")
            }

        val key = method.invoke(action, env, "newVar") as String

        assertEquals("newVar2", key)
    }
}
