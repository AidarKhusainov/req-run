package com.github.aidarkhusainov.reqrun.actions

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AddAuthConfigActionTest : BasePlatformTestCase() {
    fun testAddAuthConfigCreatesSecurityAuth() {
        val action = AddAuthConfigAction(false)
        val method = AddAuthConfigAction::class.java.getDeclaredMethod(
            "addAuthConfig",
            String::class.java,
            String::class.java
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

    fun testNextKeySkipsExistingNames() {
        val action = AddAuthConfigAction(false)
        val method = AddAuthConfigAction::class.java.getDeclaredMethod(
            "nextKey",
            JsonObject::class.java,
            String::class.java
        )
        method.isAccessible = true

        val auth = JsonObject().apply {
            addProperty("auth", "value")
            addProperty("auth1", "value")
        }

        val key = method.invoke(action, auth, "auth") as String

        assertEquals("auth2", key)
    }
}
