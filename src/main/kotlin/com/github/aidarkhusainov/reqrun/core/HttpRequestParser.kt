package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import java.net.http.HttpClient

object HttpRequestParser {
    private val requestLinePattern = Regex("^[A-Za-z]+\\s+\\S+.*$")

    fun parse(raw: String): HttpRequestSpec? {
        if (raw.isBlank()) return null

        val lines = raw.lines()
        val requestIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !isComment(trimmed) && requestLinePattern.matches(trimmed)
        }
        if (requestIndex == -1) return null

        val requestLine = lines[requestIndex].trim()
        val tokens = requestLine.split(Regex("\\s+"))
        if (tokens.size < 2) return null

        val method = tokens[0].uppercase()
        val url = tokens[1]
        val version = if (tokens.size >= 3) parseVersion(tokens.last()) else null

        val headers = mutableMapOf<String, String>()
        val bodyLines = mutableListOf<String>()
        var readingBody = false
        var headersSeen = false
        var pendingBodyStart = false
        var skipLeadingBodyBlanks = false

        for (line in lines.drop(requestIndex + 1)) {
            if (isComment(line)) {
                if (!readingBody) {
                    pendingBodyStart = true
                }
                continue
            }
            if (!readingBody) {
                if (line.isBlank()) {
                    if (headersSeen) {
                        readingBody = true
                        pendingBodyStart = false
                        skipLeadingBodyBlanks = true
                    } else {
                        pendingBodyStart = true
                    }
                    continue
                }

                if (pendingBodyStart) {
                    if (isHeaderLine(line)) {
                        pendingBodyStart = false
                        val (name, value) = splitHeader(line)
                        headers[name] = value
                        headersSeen = true
                    } else {
                        readingBody = true
                        pendingBodyStart = false
                        skipLeadingBodyBlanks = false
                        bodyLines += line
                    }
                    continue
                }

                if (isHeaderLine(line)) {
                    val (name, value) = splitHeader(line)
                    headers[name] = value
                    headersSeen = true
                } else {
                    // Ignore malformed header lines until a blank line starts the body.
                }
            } else {
                if (skipLeadingBodyBlanks && line.isBlank()) {
                    continue
                }
                skipLeadingBodyBlanks = false
                bodyLines += line
            }
        }

        val body = bodyLines.joinToString("\n").ifBlank { null }
        return HttpRequestSpec(method, url, headers, body, version)
    }

    private fun isComment(line: String): Boolean = line.trimStart().startsWith("#")

    private fun isHeaderLine(line: String): Boolean {
        val separatorIndex = line.indexOf(':')
        if (separatorIndex <= 0) return false
        return line.substring(0, separatorIndex).trim().isNotEmpty()
    }

    private fun splitHeader(line: String): Pair<String, String> {
        val separatorIndex = line.indexOf(':')
        val name = line.substring(0, separatorIndex).trim()
        val value = line.substring(separatorIndex + 1).trim()
        return name to value
    }

    private fun parseVersion(token: String): HttpClient.Version? = when (token) {
        "HTTP/1.1" -> HttpClient.Version.HTTP_1_1
        "HTTP/2" -> HttpClient.Version.HTTP_2
        else -> null
    }
}
