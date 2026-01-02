package com.github.aidarkhusainov.reqrun.core

import java.time.Instant
import java.util.UUID
import kotlin.random.Random

interface AuthTokenResolver {
    fun resolveToken(id: String, variables: Map<String, String>, builtins: Map<String, String>): String?
    fun resolveHeader(id: String, variables: Map<String, String>, builtins: Map<String, String>): String? = null
}

object VariableResolver {
    private val variableDefinitionPattern = Regex("^\\s*@([A-Za-z0-9_.-]+)\\s*=\\s*(.*)$")
    private val placeholderPattern = Regex("\\{\\{\\s*([^}]+?)\\s*\\}\\}")
    private val authTokenPattern = Regex("^\\\$?auth\\.token\\((.*)\\)$")
    private val authHeaderPattern = Regex("^\\\$?auth\\.header\\((.*)\\)$")

    fun collectFileVariables(fileText: String): Map<String, String> {
        val vars = LinkedHashMap<String, String>()
        for (line in fileText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("###")) continue
            val match = variableDefinitionPattern.find(line) ?: continue
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            vars[name] = value
        }
        return vars
    }

    fun resolveRequest(
        rawRequest: String,
        fileVariables: Map<String, String>,
        environmentVariables: Map<String, String> = emptyMap(),
        authTokenResolver: AuthTokenResolver? = null,
    ): String {
        val builtins = builtinVariables()
        val combined = LinkedHashMap<String, String>()
        combined.putAll(environmentVariables)
        combined.putAll(fileVariables)
        val resolvedVariables = resolveVariables(combined, builtins, authTokenResolver)
        val cleaned = stripVariableLines(rawRequest)
        return resolvePlaceholders(cleaned, resolvedVariables, builtins, authTokenResolver)
    }

    fun findUnresolvedPlaceholders(text: String): Set<String> {
        val withoutComments = text.lineSequence()
            .filterNot { it.trimStart().startsWith("#") }
            .joinToString("\n")
        return placeholderPattern.findAll(withoutComments)
            .map { it.groupValues[1].trim() }
            .toSet()
    }

    fun formatUnresolved(unresolved: Set<String>, limit: Int = 5): String {
        if (unresolved.isEmpty()) return ""
        val sorted = unresolved.sorted()
        val shown = sorted.take(limit)
        val suffix = if (sorted.size > limit) " (+${sorted.size - limit} more)" else ""
        return shown.joinToString(", ") + suffix
    }

    private fun resolveVariables(
        raw: Map<String, String>,
        builtins: Map<String, String>,
        authTokenResolver: AuthTokenResolver?,
    ): Map<String, String> {
        var current = LinkedHashMap(raw)
        repeat(5) {
            var changed = false
            val next = LinkedHashMap<String, String>()
            for ((key, value) in current) {
                val resolved = resolvePlaceholders(value, current, builtins, authTokenResolver)
                if (resolved != value) changed = true
                next[key] = resolved
            }
            current = next
            if (!changed) return current
        }
        return current
    }

    private fun stripVariableLines(rawRequest: String): String {
        val lines = rawRequest.lineSequence().filterNot { line ->
            variableDefinitionPattern.matches(line)
        }
        return lines.joinToString("\n")
    }

    private fun resolvePlaceholders(
        text: String,
        variables: Map<String, String>,
        builtins: Map<String, String>,
        authTokenResolver: AuthTokenResolver?,
    ): String {
        return placeholderPattern.replace(text) { match ->
            val token = match.groupValues[1].trim()
            val authHeaderId = parseAuthHeaderId(token)
            if (authHeaderId != null) {
                val resolved = authTokenResolver?.resolveHeader(authHeaderId, variables, builtins)
                return@replace resolved ?: match.value
            }
            val authId = parseAuthTokenId(token)
            if (authId != null) {
                val resolved = authTokenResolver?.resolveToken(authId, variables, builtins)
                return@replace resolved ?: match.value
            }
            if (token.startsWith("$")) {
                val builtin = token.removePrefix("$")
                builtins[builtin] ?: match.value
            } else {
                variables[token] ?: match.value
            }
        }
    }

    fun resolveValue(
        value: String,
        variables: Map<String, String>,
        builtins: Map<String, String>,
        authTokenResolver: AuthTokenResolver? = null,
    ): String {
        return resolvePlaceholders(value, variables, builtins, authTokenResolver)
    }

    fun extractAuthTokenId(token: String): String? {
        return parseAuthTokenId(token)
    }

    fun extractAuthHeaderId(token: String): String? {
        return parseAuthHeaderId(token)
    }

    fun builtins(): Map<String, String> {
        return builtinVariables()
    }

    private fun parseAuthTokenId(token: String): String? {
        val match = authTokenPattern.matchEntire(token) ?: return null
        return parseAuthId(match.groupValues[1])
    }

    private fun parseAuthHeaderId(token: String): String? {
        val match = authHeaderPattern.matchEntire(token) ?: return null
        return parseAuthId(match.groupValues[1])
    }

    private fun parseAuthId(rawValue: String): String? {
        val raw = rawValue.trim()
        if (raw.isEmpty()) return null
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length - 1).trim().takeIf { it.isNotEmpty() }
        }
        return raw
    }

    private fun builtinVariables(): Map<String, String> {
        return mapOf(
            "timestamp" to Instant.now().epochSecond.toString(),
            "uuid" to UUID.randomUUID().toString(),
            "randomInt" to Random.nextInt(0, 1001).toString(),
        )
    }
}
