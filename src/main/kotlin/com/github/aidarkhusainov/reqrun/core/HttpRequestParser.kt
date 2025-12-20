package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec

object HttpRequestParser {
    fun parse(raw: String): HttpRequestSpec? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val lines = trimmed.lines()
        val requestLine = lines.first().trim()
        val requestParts = requestLine.split(Regex("\\s+"), limit = 2)
        if (requestParts.size != 2) return null

        val method = requestParts[0].uppercase()
        val url = requestParts[1]

        val headers = mutableMapOf<String, String>()
        val bodyLines = mutableListOf<String>()
        var readingBody = false

        for (line in lines.drop(1)) {
            if (!readingBody && line.isBlank()) {
                readingBody = true
                continue
            }

            if (readingBody) {
                bodyLines += line
            } else {
                val separatorIndex = line.indexOf(':')
                if (separatorIndex > 0) {
                    val name = line.substring(0, separatorIndex).trim()
                    val value = line.substring(separatorIndex + 1).trim()
                    if (name.isNotEmpty()) {
                        headers[name] = value
                    }
                }
            }
        }

        val body = bodyLines.joinToString("\n").ifBlank { null }
        return HttpRequestSpec(method, url, headers, body)
    }
}
