package com.github.aidarkhusainov.reqrun.core

import java.time.Instant
import java.util.UUID
import kotlin.random.Random

object VariableResolver {
    private val variableDefinitionPattern = Regex("^\\s*@([A-Za-z0-9_.-]+)\\s*=\\s*(.*)$")
    private val placeholderPattern = Regex("\\{\\{\\s*([^}]+?)\\s*\\}\\}")

    fun collectFileVariables(fileText: String): Map<String, String> {
        val vars = LinkedHashMap<String, String>()
        for (line in fileText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed == "###") continue
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
        environmentVariables: Map<String, String> = emptyMap()
    ): String {
        val builtins = builtinVariables()
        val combined = LinkedHashMap<String, String>()
        combined.putAll(environmentVariables)
        combined.putAll(fileVariables)
        val resolvedVariables = resolveVariables(combined, builtins)
        val cleaned = stripVariableLines(rawRequest)
        return resolvePlaceholders(cleaned, resolvedVariables, builtins)
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

    private fun resolveVariables(raw: Map<String, String>, builtins: Map<String, String>): Map<String, String> {
        var current = LinkedHashMap(raw)
        repeat(5) {
            var changed = false
            val next = LinkedHashMap<String, String>()
            for ((key, value) in current) {
                val resolved = resolvePlaceholders(value, current, builtins)
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
        builtins: Map<String, String>
    ): String {
        return placeholderPattern.replace(text) { match ->
            val token = match.groupValues[1].trim()
            if (token.startsWith("$")) {
                val builtin = token.removePrefix("$")
                builtins[builtin] ?: match.value
            } else {
                variables[token] ?: match.value
            }
        }
    }

    private fun builtinVariables(): Map<String, String> {
        return mapOf(
            "timestamp" to Instant.now().epochSecond.toString(),
            "uuid" to UUID.randomUUID().toString(),
            "randomInt" to Random.nextInt(0, 1001).toString(),
        )
    }
}
