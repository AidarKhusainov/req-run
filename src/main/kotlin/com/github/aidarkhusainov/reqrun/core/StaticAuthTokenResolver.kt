package com.github.aidarkhusainov.reqrun.core

import java.nio.charset.StandardCharsets
import java.util.Base64

object StaticAuthTokenResolver {
    fun createResolver(configs: Map<String, AuthConfig>): AuthTokenResolver =
        object : AuthTokenResolver {
            override fun resolveToken(
                id: String,
                variables: Map<String, String>,
                builtins: Map<String, String>,
            ): String? = resolveToken(id, configs, variables, builtins)

            override fun resolveHeader(
                id: String,
                variables: Map<String, String>,
                builtins: Map<String, String>,
            ): String? = resolveHeader(id, configs, variables, builtins)
        }

    fun resolveToken(
        id: String,
        configs: Map<String, AuthConfig>,
        variables: Map<String, String>,
        builtins: Map<String, String>,
    ): String? {
        val config = configs[id] ?: return null
        if (config.type != AuthType.STATIC) return null
        return when (config.scheme) {
            AuthScheme.BEARER,
            AuthScheme.API_KEY,
            -> resolvedValue(config.token, variables, builtins)
            AuthScheme.BASIC -> resolveBasicToken(config, variables, builtins)
        }
    }

    fun resolveHeader(
        id: String,
        configs: Map<String, AuthConfig>,
        variables: Map<String, String>,
        builtins: Map<String, String>,
    ): String? {
        val config = configs[id] ?: return null
        if (config.type != AuthType.STATIC) return null
        val token = resolveToken(id, configs, variables, builtins) ?: return null
        val headerName =
            resolvedValue(config.header, variables, builtins)
                ?: defaultHeaderName(config.scheme)
        val headerValue =
            when (config.scheme) {
                AuthScheme.BEARER -> withPrefix("Bearer", token)
                AuthScheme.BASIC -> withPrefix("Basic", token)
                AuthScheme.API_KEY -> token
            }
        return "$headerName: $headerValue"
    }

    fun describeAuthIssue(
        id: String,
        configs: Map<String, AuthConfig>,
        variables: Map<String, String>,
        builtins: Map<String, String>,
    ): String? {
        val config = configs[id] ?: return null
        if (config.type != AuthType.STATIC) return null
        val tokenInfo = analyzeField(config.token, variables, builtins)
        val userInfo = analyzeField(config.username, variables, builtins)
        val passInfo = analyzeField(config.password, variables, builtins)
        return when (config.scheme) {
            AuthScheme.BASIC -> {
                if (config.token != null) {
                    tokenIssue("Token", tokenInfo)?.let { "Auth config '$id' $it" }
                } else {
                    val userOk = userInfo.value != null && userInfo.unresolved.isEmpty()
                    val passOk = passInfo.value != null && passInfo.unresolved.isEmpty()
                    if (userOk && passOk) return null
                    val issues =
                        listOfNotNull(
                            tokenIssue("Username", userInfo),
                            tokenIssue("Password", passInfo),
                        )
                    "Auth config '$id' " + issues.joinToString(" ")
                }
            }
            AuthScheme.BEARER, AuthScheme.API_KEY -> {
                tokenIssue("Token", tokenInfo)?.let { "Auth config '$id' $it" }
            }
        }
    }

    private fun resolveBasicToken(
        config: AuthConfig,
        variables: Map<String, String>,
        builtins: Map<String, String>,
    ): String? {
        val token = resolvedValue(config.token, variables, builtins)
        if (token != null) return token
        val username = resolvedValue(config.username, variables, builtins) ?: return null
        val password = resolvedValue(config.password, variables, builtins) ?: return null
        val raw = "$username:$password"
        return Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    private fun analyzeField(
        value: String?,
        variables: Map<String, String>,
        builtins: Map<String, String>,
    ): FieldInfo {
        if (value == null) return FieldInfo(null, emptySet())
        val resolved = VariableResolver.resolveValue(value, variables, builtins)
        val unresolved = VariableResolver.findUnresolvedPlaceholders(resolved)
        val trimmed = resolved.trim()
        return FieldInfo(trimmed.takeIf { it.isNotEmpty() }, unresolved)
    }

    private fun tokenIssue(
        label: String,
        info: FieldInfo,
    ): String? =
        when {
            info.value == null && info.unresolved.isEmpty() -> "$label is missing."
            info.unresolved.isNotEmpty() -> {
                val formatted = VariableResolver.formatUnresolved(info.unresolved)
                "$label has unresolved variables: $formatted."
            }
            info.value != null -> null
            else -> "$label is empty."
        }

    private fun defaultHeaderName(scheme: AuthScheme): String =
        when (scheme) {
            AuthScheme.BEARER, AuthScheme.BASIC -> "Authorization"
            AuthScheme.API_KEY -> "X-API-Key"
        }

    private fun withPrefix(
        prefix: String,
        token: String,
    ): String {
        val trimmed = token.trimStart()
        return if (trimmed.startsWith(prefix, ignoreCase = true)) token else "$prefix $token"
    }

    private fun resolvedValue(
        value: String?,
        variables: Map<String, String>,
        builtins: Map<String, String>,
    ): String? {
        val resolved = value?.let { VariableResolver.resolveValue(it, variables, builtins) }?.trim()
        return resolved?.takeIf { it.isNotEmpty() }
    }

    private data class FieldInfo(
        val value: String?,
        val unresolved: Set<String>,
    )
}
