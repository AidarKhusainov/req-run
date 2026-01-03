package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.BodyPart
import com.github.aidarkhusainov.reqrun.model.CompositeBody
import com.github.aidarkhusainov.reqrun.model.FileResponseTarget
import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.RequestBodySpec
import com.github.aidarkhusainov.reqrun.model.ResponseTarget
import com.github.aidarkhusainov.reqrun.model.TextBody
import java.net.http.HttpClient
import java.nio.file.InvalidPathException
import java.nio.file.Path

object HttpRequestParser {
    private val requestLinePattern = Regex("^[A-Za-z]+\\s+\\S+.*$")

    fun parse(raw: String, baseDir: Path? = null): HttpRequestSpec? {
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
        var responseTarget: ResponseTarget? = null
        var bodyTerminated = false

        for (line in lines.drop(requestIndex + 1)) {
            if (bodyTerminated) continue
            if (isComment(line)) {
                if (!readingBody) {
                    pendingBodyStart = true
                }
                continue
            }
            if (!readingBody) {
                parseResponseTarget(line, baseDir)?.let {
                    responseTarget = it
                    continue
                }
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
                parseResponseTarget(line, baseDir)?.let {
                    responseTarget = it
                    bodyTerminated = true
                    continue
                }
                if (skipLeadingBodyBlanks && line.isBlank()) {
                    continue
                }
                skipLeadingBodyBlanks = false
                bodyLines += line
            }
        }

        val body = parseBody(bodyLines, baseDir)
        return HttpRequestSpec(method, url, headers, body, version, responseTarget)
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

    private fun parseBody(bodyLines: List<String>, baseDir: Path?): RequestBodySpec? {
        val preview = bodyLines.joinToString("\n").ifBlank { return null }
        val parts = mutableListOf<BodyPart>()
        val textBuilder = StringBuilder()
        for (i in bodyLines.indices) {
            val line = bodyLines[i]
            val suffix = if (i < bodyLines.lastIndex) "\n" else ""
            val filePath = parseFileDirective(line, baseDir)
            if (filePath != null) {
                if (textBuilder.isNotEmpty()) {
                    parts += BodyPart.Text(textBuilder.toString())
                    textBuilder.setLength(0)
                }
                parts += BodyPart.File(filePath)
                if (suffix.isNotEmpty()) {
                    textBuilder.append(suffix)
                }
            } else {
                textBuilder.append(line).append(suffix)
            }
        }
        if (textBuilder.isNotEmpty()) {
            parts += BodyPart.Text(textBuilder.toString())
        }
        val hasFilePart = parts.any { it is BodyPart.File }
        return if (hasFilePart) CompositeBody(preview, parts) else TextBody(preview)
    }

    private fun parseResponseTarget(line: String, baseDir: Path?): ResponseTarget? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith(">")) return null
        val append = trimmed.startsWith(">>")
        val raw = if (append) trimmed.drop(2) else trimmed.drop(1)
        if (raw.isEmpty() || !raw.first().isWhitespace()) return null
        val pathText = unquote(raw.trim()).takeIf { it.isNotBlank() } ?: return null
        val path = try {
            resolvePath(pathText, baseDir)
        } catch (_: InvalidPathException) {
            return null
        }
        return FileResponseTarget(path, append)
    }

    private fun parseFileDirective(line: String, baseDir: Path?): Path? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("<")) return null
        val raw = trimmed.drop(1)
        if (raw.isEmpty() || !raw.first().isWhitespace()) return null
        val pathText = unquote(raw.trim()).takeIf { it.isNotBlank() } ?: return null
        return try {
            resolvePath(pathText, baseDir)
        } catch (_: InvalidPathException) {
            null
        }
    }

    private fun resolvePath(pathText: String, baseDir: Path?): Path {
        val path = Path.of(pathText)
        return if (path.isAbsolute || baseDir == null) path.normalize() else baseDir.resolve(path).normalize()
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length - 1).trim()
            }
        }
        return trimmed
    }
}
