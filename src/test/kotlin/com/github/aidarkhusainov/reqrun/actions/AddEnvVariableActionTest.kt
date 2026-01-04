package com.github.aidarkhusainov.reqrun.actions

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
