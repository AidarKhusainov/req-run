package com.github.aidarkhusainov.reqrun.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class VariableResolverTest {
    @Test
    fun `resolveRequest substitutes file variables and builtins`() {
        val fileText = """
            @baseUrl = https://example.com
            @token = abc123
        """.trimIndent()
        val raw = """
            GET {{baseUrl}}/users
            Authorization: Bearer {{token}}
            X-Req: {{${'$'}uuid}}
            X-Time: {{${'$'}timestamp}}
        """.trimIndent()

        val vars = VariableResolver.collectFileVariables(fileText)
        val resolved = VariableResolver.resolveRequest(raw, vars)

        assertTrue(resolved.contains("GET https://example.com/users"))
        assertTrue(resolved.contains("Authorization: Bearer abc123"))
        assertTrue(Regex("X-Req: [0-9a-fA-F-]{36}").containsMatchIn(resolved))
        assertTrue(Regex("X-Time: \\d+").containsMatchIn(resolved))
    }

    @Test
    fun `resolveRequest supports variable references`() {
        val fileText = """
            @host = api.example.com
            @baseUrl = https://{{host}}
        """.trimIndent()
        val raw = "GET {{baseUrl}}/v1"

        val vars = VariableResolver.collectFileVariables(fileText)
        val resolved = VariableResolver.resolveRequest(raw, vars)

        assertTrue(resolved.contains("GET https://api.example.com/v1"))
    }

    @Test
    fun `resolveRequest prefers file variables over environment variables`() {
        val envVars = mapOf("baseUrl" to "https://env.example.com")
        val fileText = "@baseUrl = https://file.example.com"
        val raw = "GET {{baseUrl}}/v1"

        val fileVars = VariableResolver.collectFileVariables(fileText)
        val resolved = VariableResolver.resolveRequest(raw, fileVars, envVars)

        assertTrue(resolved.contains("GET https://file.example.com/v1"))
    }

    @Test
    fun `resolveRequest strips variable lines and resolves randomInt`() {
        val fileText = "@baseUrl = https://example.com"
        val raw = """
            @token = secret
            GET {{baseUrl}}/v1
            X-Rand: {{${'$'}randomInt}}
        """.trimIndent()

        val vars = VariableResolver.collectFileVariables(fileText)
        val resolved = VariableResolver.resolveRequest(raw, vars)

        assertFalse(resolved.contains("@token"))
        val match = Regex("X-Rand: (\\d+)").find(resolved)
        assertTrue(match != null)
        val value = match!!.groupValues[1].toInt()
        assertTrue(value in 0..1000)
    }

    @Test
    fun `findUnresolvedPlaceholders ignores comments`() {
        val raw = """
            # {{ignoreMe}}
            GET {{missing}}/v1
        """.trimIndent()

        val unresolved = VariableResolver.findUnresolvedPlaceholders(raw)

        assertEquals(setOf("missing"), unresolved)
    }

    @Test
    fun `formatUnresolved sorts and limits output`() {
        val unresolved = setOf("z", "a", "b", "c", "d", "e")

        val formatted = VariableResolver.formatUnresolved(unresolved, limit = 3)

        assertEquals("a, b, c (+3 more)", formatted)
    }
}
