package com.github.aidarkhusainov.reqrun.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StaticAuthTokenResolverTest {
    @Test
    fun `resolves bearer token with variables`() {
        val configs =
            mapOf(
                "bearer" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.BEARER,
                        token = "{{token}}",
                        username = null,
                        password = null,
                        header = null,
                    ),
            )

        val token =
            StaticAuthTokenResolver.resolveToken(
                id = "bearer",
                configs = configs,
                variables = mapOf("token" to "abc123"),
                builtins = emptyMap<String, String>(),
            )

        assertEquals("abc123", token)
    }

    @Test
    fun `resolves basic token from username and password`() {
        val configs =
            mapOf(
                "basic" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.BASIC,
                        token = null,
                        username = "user",
                        password = "pass",
                        header = null,
                    ),
            )

        val token =
            StaticAuthTokenResolver.resolveToken(
                id = "basic",
                configs = configs,
                variables = emptyMap<String, String>(),
                builtins = emptyMap<String, String>(),
            )

        assertEquals("dXNlcjpwYXNz", token)
    }

    @Test
    fun `returns null for missing config`() {
        val token =
            StaticAuthTokenResolver.resolveToken(
                id = "missing",
                configs = emptyMap<String, AuthConfig>(),
                variables = emptyMap<String, String>(),
                builtins = emptyMap<String, String>(),
            )

        assertNull(token)
    }

    @Test
    fun `basic token uses explicit token when present`() {
        val configs =
            mapOf(
                "basic" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.BASIC,
                        token = "{{raw}}",
                        username = "user",
                        password = "pass",
                        header = null,
                    ),
            )

        val token =
            StaticAuthTokenResolver.resolveToken(
                id = "basic",
                configs = configs,
                variables = mapOf("raw" to "dXNlcjpwYXNz"),
                builtins = emptyMap<String, String>(),
            )

        assertEquals("dXNlcjpwYXNz", token)
    }

    @Test
    fun `returns null when basic has missing credentials`() {
        val configs =
            mapOf(
                "basic" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.BASIC,
                        token = null,
                        username = "user",
                        password = null,
                        header = null,
                    ),
            )

        val token =
            StaticAuthTokenResolver.resolveToken(
                id = "basic",
                configs = configs,
                variables = emptyMap<String, String>(),
                builtins = emptyMap<String, String>(),
            )

        assertNull(token)
    }

    @Test
    fun `api key resolves token`() {
        val configs =
            mapOf(
                "api" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.API_KEY,
                        token = "secret",
                        username = null,
                        password = null,
                        header = null,
                    ),
            )

        val token =
            StaticAuthTokenResolver.resolveToken(
                id = "api",
                configs = configs,
                variables = emptyMap<String, String>(),
                builtins = emptyMap<String, String>(),
            )

        assertEquals("secret", token)
    }

    @Test
    fun `resolves bearer header with prefix`() {
        val configs =
            mapOf(
                "bearer" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.BEARER,
                        token = "abc",
                        username = null,
                        password = null,
                        header = null,
                    ),
            )

        val header =
            StaticAuthTokenResolver.resolveHeader(
                id = "bearer",
                configs = configs,
                variables = emptyMap<String, String>(),
                builtins = emptyMap<String, String>(),
            )

        assertEquals("Authorization: Bearer abc", header)
    }

    @Test
    fun `resolves api key header with custom name`() {
        val configs =
            mapOf(
                "api" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.API_KEY,
                        token = "secret",
                        username = null,
                        password = null,
                        header = "X-API-Token",
                    ),
            )

        val header =
            StaticAuthTokenResolver.resolveHeader(
                id = "api",
                configs = configs,
                variables = emptyMap<String, String>(),
                builtins = emptyMap<String, String>(),
            )

        assertEquals("X-API-Token: secret", header)
    }

    @Test
    fun `resolves header name with placeholders`() {
        val configs =
            mapOf(
                "api" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.API_KEY,
                        token = "{{token}}",
                        username = null,
                        password = null,
                        header = "{{headerName}}",
                    ),
            )

        val header =
            StaticAuthTokenResolver.resolveHeader(
                id = "api",
                configs = configs,
                variables = mapOf("token" to "secret", "headerName" to "X-Api-Key"),
                builtins = emptyMap<String, String>(),
            )

        assertEquals("X-Api-Key: secret", header)
    }

    @Test
    fun `describeAuthIssue reports missing token`() {
        val configs =
            mapOf(
                "bearer" to
                    AuthConfig(
                        type = AuthType.STATIC,
                        scheme = AuthScheme.BEARER,
                        token = null,
                        username = null,
                        password = null,
                        header = null,
                    ),
            )

        val issue =
            StaticAuthTokenResolver.describeAuthIssue(
                id = "bearer",
                configs = configs,
                variables = emptyMap<String, String>(),
                builtins = emptyMap<String, String>(),
            )

        assertEquals("Auth config 'bearer' Token is missing.", issue)
    }
}
