package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.BodyPart
import com.github.aidarkhusainov.reqrun.model.CompositeBody
import com.github.aidarkhusainov.reqrun.model.FileResponseTarget
import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.RequestBodySpec
import com.github.aidarkhusainov.reqrun.model.RequestOptions
import com.github.aidarkhusainov.reqrun.model.ResponseTarget
import com.github.aidarkhusainov.reqrun.model.TextBody
import com.github.aidarkhusainov.reqrun.model.TlsOptions
import java.net.http.HttpClient
import java.nio.file.InvalidPathException
import java.nio.file.Path

object HttpRequestParser {
    private val requestLinePattern = Regex("^[A-Za-z]+\\s+\\S+.*$")

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "ReturnCount")
    fun parse(
        raw: String,
        baseDir: Path? = null,
    ): HttpRequestSpec? {
        if (raw.isBlank()) return null

        val lines = raw.lines()
        val requestIndex =
            lines.indexOfFirst { line ->
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
        val optionsBuilder = RequestOptionsBuilder(baseDir)

        for (line in lines.take(requestIndex)) {
            parseOptionsDirective(line, optionsBuilder)
        }

        for (line in lines.drop(requestIndex + 1)) {
            if (bodyTerminated) continue
            if (isComment(line)) {
                if (!readingBody) {
                    parseOptionsDirective(line, optionsBuilder)
                }
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
        val options = optionsBuilder.build()
        return HttpRequestSpec(method, url, headers, body, version, responseTarget, options)
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

    private fun parseVersion(token: String): HttpClient.Version? =
        when (token) {
            "HTTP/1.1" -> HttpClient.Version.HTTP_1_1
            "HTTP/2" -> HttpClient.Version.HTTP_2
            else -> null
        }

    private fun parseOptionsDirective(
        line: String,
        builder: RequestOptionsBuilder,
    ) {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("#")) return
        val content = trimmed.removePrefix("#").trimStart()
        if (!content.startsWith("@reqrun.")) return
        val rest = content.removePrefix("@reqrun.").trimStart()
        val separatorIndex = rest.indexOfFirst { it == ' ' || it == '\t' || it == '=' }
        val key = if (separatorIndex == -1) rest else rest.substring(0, separatorIndex)
        val rawValue = if (separatorIndex == -1) "" else rest.substring(separatorIndex + 1).trim()
        val value = unquote(rawValue)
        builder.applyOption(key, value)
    }

    private class RequestOptionsBuilder(
        private val baseDir: Path?,
    ) {
        private var proxyUrl: String? = null
        private var proxyUser: String? = null
        private var connectTimeoutMillis: Long? = null
        private var maxTimeMillis: Long? = null
        private var retryCount: Int? = null
        private var retryDelayMillis: Long? = null
        private var retryMaxTimeMillis: Long? = null
        private var cookieJarPath: String? = null
        private var caCertPath: String? = null
        private var clientCertPath: String? = null
        private var clientKeyPath: String? = null
        private var clientCertPassword: String? = null
        private var clientKeyPassword: String? = null
        private var unixSocketPath: String? = null

        fun applyOption(
            key: String,
            value: String,
        ) {
            when (key) {
                "proxy" -> proxyUrl = value.takeIf { it.isNotBlank() }
                "proxy-user" -> proxyUser = value.takeIf { it.isNotBlank() }
                "connect-timeout" -> connectTimeoutMillis = parseSeconds(value)
                "max-time" -> maxTimeMillis = parseSeconds(value)
                "retry" -> retryCount = value.toIntOrNull()
                "retry-delay" -> retryDelayMillis = parseSeconds(value)
                "retry-max-time" -> retryMaxTimeMillis = parseSeconds(value)
                "cookie-jar" -> cookieJarPath = resolvePath(value)
                "cacert" -> caCertPath = resolvePath(value)
                "cert" -> {
                    val (path, password) = splitPassword(value)
                    clientCertPath = resolvePath(path)
                    clientCertPassword = password
                }

                "key" -> {
                    val (path, password) = splitPassword(value)
                    clientKeyPath = resolvePath(path)
                    clientKeyPassword = password
                }

                "unix-socket" -> unixSocketPath = resolvePath(value)
            }
        }

        fun build(): RequestOptions =
            RequestOptions(
                proxyUrl = proxyUrl,
                proxyUser = proxyUser,
                connectTimeoutMillis = connectTimeoutMillis,
                maxTimeMillis = maxTimeMillis,
                retryCount = retryCount,
                retryDelayMillis = retryDelayMillis,
                retryMaxTimeMillis = retryMaxTimeMillis,
                cookieJarPath = cookieJarPath,
                tls =
                    if (caCertPath != null || clientCertPath != null || clientKeyPath != null) {
                        TlsOptions(
                            caCertPath = caCertPath,
                            clientCertPath = clientCertPath,
                            clientKeyPath = clientKeyPath,
                            clientCertPassword = clientCertPassword,
                            clientKeyPassword = clientKeyPassword,
                        )
                    } else {
                        null
                    },
                unixSocketPath = unixSocketPath,
            )

        private fun parseSeconds(value: String): Long? {
            val seconds = value.toDoubleOrNull() ?: return null
            if (seconds < 0.0) return null
            return kotlin.math.round(seconds * 1000.0).toLong()
        }

        private fun splitPassword(value: String): Pair<String, String?> {
            val index = value.lastIndexOf(':')
            if (index <= 0) return value to null
            val path = value.substring(0, index)
            val password = value.substring(index + 1).takeIf { it.isNotEmpty() }
            return path to password
        }

        private fun resolvePath(value: String): String? {
            if (value.isBlank()) return null
            return try {
                val path = Path.of(value)
                if (path.isAbsolute || baseDir == null) {
                    path.toString()
                } else {
                    baseDir.resolve(path).toString()
                }
            } catch (_: InvalidPathException) {
                null
            }
        }
    }

    private fun parseBody(
        bodyLines: List<String>,
        baseDir: Path?,
    ): RequestBodySpec? {
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

    @Suppress("ReturnCount")
    private fun parseResponseTarget(
        line: String,
        baseDir: Path?,
    ): ResponseTarget? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith(">")) return null
        val append = trimmed.startsWith(">>")
        val raw = if (append) trimmed.drop(2) else trimmed.drop(1)
        if (raw.isEmpty() || !raw.first().isWhitespace()) return null
        val pathText = unquote(raw.trim()).takeIf { it.isNotBlank() } ?: return null
        val path =
            try {
                resolvePath(pathText, baseDir)
            } catch (_: InvalidPathException) {
                return null
            }
        return FileResponseTarget(path, append)
    }

    @Suppress("ReturnCount")
    private fun parseFileDirective(
        line: String,
        baseDir: Path?,
    ): Path? {
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

    private fun resolvePath(
        pathText: String,
        baseDir: Path?,
    ): Path {
        val path = Path.of(pathText)
        return if (path.isAbsolute || baseDir == null) path.normalize() else baseDir.resolve(path).normalize()
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            val isQuoted =
                (first == '"' && last == '"') || (first == '\'' && last == '\'')
            if (isQuoted) {
                return trimmed.substring(1, trimmed.length - 1).trim()
            }
        }
        return trimmed
    }
}
