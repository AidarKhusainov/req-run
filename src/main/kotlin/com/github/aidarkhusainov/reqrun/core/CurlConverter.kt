package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.*
import kotlin.math.roundToLong

object CurlConverter {
    data class CurlParseError(
        val message: String,
        val offset: Int,
    )

    data class CurlParseWarning(
        val message: String,
        val offset: Int,
    )

    data class CurlParseResult(
        val spec: HttpRequestSpec?,
        val error: CurlParseError?,
        val warnings: List<CurlParseWarning> = emptyList(),
    )

    fun toCurl(spec: HttpRequestSpec): String {
        val parts = mutableListOf("curl", "-X", spec.method, shellQuote(spec.url))
        spec.headers.forEach { (name, value) ->
            parts += "-H"
            parts += shellQuote("$name: $value")
        }
        spec.body?.let { body ->
            when (body) {
                is TextBody -> {
                    parts += "--data"
                    parts += shellQuote(body.preview)
                }

                is CompositeBody -> {
                    val singleFile = body.parts.singleOrNull() as? BodyPart.File
                    if (singleFile != null) {
                        parts += "--data-binary"
                        parts += shellQuote("@${singleFile.path}")
                    } else {
                        parts += "--data-binary"
                        parts += shellQuote(body.preview)
                    }
                }
            }
        }
        return parts.joinToString(" ")
    }

    fun toHttp(spec: HttpRequestSpec): String {
        val sb = StringBuilder()
        appendOptions(sb, spec.options)
        sb.append(spec.method).append(' ').append(spec.url)
        spec.version?.let { sb.append(' ').append(formatVersion(it)) }
        if (spec.headers.isNotEmpty()) {
            sb.append('\n')
            spec.headers.forEach { (name, value) ->
                sb
                    .append(name)
                    .append(": ")
                    .append(value)
                    .append('\n')
            }
            sb.setLength(sb.length - 1)
        }
        spec.body?.let { body ->
            sb.append("\n\n").append(body.preview)
        }
        spec.responseTarget?.let { target ->
            if (spec.body == null) {
                sb.append('\n')
            }
            val prefix = if (target.append) ">>" else ">"
            sb
                .append('\n')
                .append(prefix)
                .append(' ')
                .append(target.path)
        }
        return sb.toString()
    }

    fun fromCurl(text: String): HttpRequestSpec? = fromCurlDetailed(text).spec

    fun fromCurlDetailed(
        text: String,
        configLoader: ((String) -> String?)? = null,
    ): CurlParseResult {
        val tokens = tokenizeWithRanges(text).toMutableList()
        if (tokens.isEmpty()) {
            return CurlParseResult(null, CurlParseError("Empty cURL command.", 0), emptyList())
        }
        var i = 0
        if (!tokens[0].text.equals("curl", ignoreCase = true)) {
            return CurlParseResult(null, CurlParseError("Not a cURL command.", tokens[0].start), emptyList())
        }
        if (tokens[0].text.equals("curl", ignoreCase = true)) {
            i = 1
        }
        var method: String? = null
        var methodExplicit = false
        var url: String? = null
        val headers = linkedMapOf<String, String>()
        val bodyParts = mutableListOf<String>()
        val formParts = mutableListOf<FormPart>()
        var version: java.net.http.HttpClient.Version? = null
        val warnings = mutableListOf<CurlParseWarning>()
        var getQuery = false
        var outputPath: String? = null
        var remoteNameRequested = false
        var proxyUrl: String? = null
        var proxyUser: String? = null
        var connectTimeoutMillis: Long? = null
        var maxTimeMillis: Long? = null
        var retryCount: Int? = null
        var retryDelayMillis: Long? = null
        var retryMaxTimeMillis: Long? = null
        var cookieJarPath: String? = null
        var caCertPath: String? = null
        var clientCertPath: String? = null
        var clientKeyPath: String? = null
        var clientCertPassword: String? = null
        var clientKeyPassword: String? = null
        var unixSocketPath: String? = null
        val loadedConfigs = mutableSetOf<String>()

        fun warn(message: String, offset: Int) {
            warnings += CurlParseWarning(message, offset)
        }

        while (i < tokens.size) {
            val token = tokens[i].text
            val tokenStart = tokens[i].start
            when {
                token == "--http1.1" -> {
                    version = java.net.http.HttpClient.Version.HTTP_1_1
                    i += 1
                }

                token == "--http2" || token == "--http2-prior-knowledge" -> {
                    version = java.net.http.HttpClient.Version.HTTP_2
                    i += 1
                }

                token == "-X" || token == "--request" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null) {
                        warn("Expected request method after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    if (value.text.isBlank()) {
                        warn("Expected request method after $token.", value.start)
                        i += 2
                        continue
                    }
                    method = value.text
                    methodExplicit = method != null
                    i += 2
                }

                token.startsWith("-X") && token.length > 2 -> {
                    method = token.substring(2)
                    methodExplicit = true
                    i += 1
                }

                token.startsWith("--request=") -> {
                    method = token.substringAfter("=")
                    if (method.isBlank()) {
                        warn("Expected request method after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    methodExplicit = true
                    i += 1
                }

                token == "-I" || token == "--head" -> {
                    method = "HEAD"
                    methodExplicit = true
                    i += 1
                }

                token == "-H" || token == "--header" -> {
                    val header = tokens.getOrNull(i + 1)
                    if (header == null) {
                        warn("Expected header value after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    val next = tokens.getOrNull(i + 2)
                    val combinedHeader =
                        if (header.text.endsWith(":") && next != null) {
                            "${header.text} ${next.text}"
                        } else {
                            header.text
                        }
                    if (!addHeader(headers, combinedHeader)) {
                        warn("Invalid header format: '$combinedHeader'.", header.start)
                    }
                    i += if (combinedHeader != header.text) 3 else 2
                }

                token.startsWith("-H") && token.length > 2 -> {
                    val header = token.substring(2)
                    val next = tokens.getOrNull(i + 1)
                    val combinedHeader =
                        if (header.endsWith(":") && next != null) {
                            "$header ${next.text}"
                        } else {
                            header
                        }
                    if (!addHeader(headers, combinedHeader)) {
                        warn("Invalid header format: '$combinedHeader'.", tokenStart)
                    }
                    i += if (combinedHeader != header) 2 else 1
                }

                token.startsWith("--header=") -> {
                    val header = token.substringAfter("=")
                    val next = tokens.getOrNull(i + 1)
                    val combinedHeader =
                        if (header.endsWith(":") && next != null) {
                            "$header ${next.text}"
                        } else {
                            header
                        }
                    if (!addHeader(headers, combinedHeader)) {
                        warn("Invalid header format: '$combinedHeader'.", tokenStart)
                    }
                    i += if (combinedHeader != header) 2 else 1
                }

                token == "-d" ||
                        token == "--data" ||
                        token == "--data-raw" ||
                        token == "--data-binary" ||
                        token == "--data-urlencode" ||
                        token == "--data-ascii" -> {
                    val data = tokens.getOrNull(i + 1)
                    if (data == null) {
                        warn("Expected data value after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    bodyParts += toBodyValue(data.text)
                    if (!methodExplicit) {
                        method = "POST"
                    }
                    i += 2
                }

                token.startsWith("-d") && token.length > 2 -> {
                    bodyParts += toBodyValue(token.substring(2))
                    if (!methodExplicit) {
                        method = "POST"
                    }
                    i += 1
                }

                token.startsWith("--data=") -> {
                    bodyParts += toBodyValue(token.substringAfter("="))
                    if (!methodExplicit) {
                        method = "POST"
                    }
                    i += 1
                }

                token.startsWith("--data-raw=") ||
                        token.startsWith("--data-binary=") ||
                        token.startsWith("--data-urlencode=") ||
                        token.startsWith("--data-ascii=") -> {
                    bodyParts += toBodyValue(token.substringAfter("="))
                    if (!methodExplicit) {
                        method = "POST"
                    }
                    i += 1
                }

                token == "-G" || token == "--get" -> {
                    getQuery = true
                    i += 1
                }

                token == "-u" || token == "--user" -> {
                    val creds = tokens.getOrNull(i + 1)
                    if (creds == null) {
                        warn("Expected credentials after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    if (creds.text.isBlank()) {
                        warn("Expected credentials after $token.", creds.start)
                        i += 2
                        continue
                    }
                    headers["Authorization"] = basicAuthHeader(creds.text)
                    i += 2
                }

                token.startsWith("-u") && token.length > 2 -> {
                    headers["Authorization"] = basicAuthHeader(token.substring(2))
                    i += 1
                }

                token.startsWith("--user=") -> {
                    headers["Authorization"] = basicAuthHeader(token.substringAfter("="))
                    i += 1
                }

                token == "-F" || token == "--form" -> {
                    val form = tokens.getOrNull(i + 1)
                    if (form == null) {
                        warn("Expected form field after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    val parsed = parseFormPart(form.text)
                    if (parsed == null) {
                        warn("Invalid form field: '${form.text}'.", form.start)
                    } else {
                        formParts += parsed
                    }
                    if (!methodExplicit) {
                        method = "POST"
                    }
                    i += 2
                }

                token.startsWith("-F") && token.length > 2 -> {
                    val parsed = parseFormPart(token.substring(2))
                    if (parsed == null) {
                        warn("Invalid form field: '${token.substring(2)}'.", tokenStart)
                    } else {
                        formParts += parsed
                    }
                    if (!methodExplicit) {
                        method = "POST"
                    }
                    i += 1
                }

                token.startsWith("--form=") -> {
                    val parsed = parseFormPart(token.substringAfter("="))
                    if (parsed == null) {
                        warn("Invalid form field: '${token.substringAfter("=")}'.", tokenStart)
                    } else {
                        formParts += parsed
                    }
                    if (!methodExplicit) {
                        method = "POST"
                    }
                    i += 1
                }

                token == "--url" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null) {
                        warn("Expected URL after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    if (value.text.isBlank()) {
                        warn("Expected URL after $token.", value.start)
                        i += 2
                        continue
                    }
                    url = value.text
                    i += 2
                }

                token.startsWith("--url=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected URL after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    url = value
                    i += 1
                }

                token == "-o" || token == "--output" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected output path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    outputPath = value.text
                    i += 2
                }

                token.startsWith("-o") && token.length > 2 -> {
                    val value = token.substring(2)
                    if (value.isBlank()) {
                        warn("Expected output path after -o.", tokenStart)
                        i += 1
                        continue
                    }
                    outputPath = value
                    i += 1
                }

                token.startsWith("--output=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected output path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    outputPath = value
                    i += 1
                }

                token == "-O" || token == "--remote-name" -> {
                    remoteNameRequested = true
                    i += 1
                }

                token == "-A" || token == "--user-agent" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected user agent after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["User-Agent"] = value.text
                    i += 2
                }

                token.startsWith("-A") && token.length > 2 -> {
                    val value = token.substring(2)
                    if (value.isBlank()) {
                        warn("Expected user agent after -A.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["User-Agent"] = value
                    i += 1
                }

                token.startsWith("--user-agent=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected user agent after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["User-Agent"] = value
                    i += 1
                }

                token == "-e" || token == "--referer" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected referer after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["Referer"] = value.text
                    i += 2
                }

                token.startsWith("-e") && token.length > 2 -> {
                    val value = token.substring(2)
                    if (value.isBlank()) {
                        warn("Expected referer after -e.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["Referer"] = value
                    i += 1
                }

                token.startsWith("--referer=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected referer after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["Referer"] = value
                    i += 1
                }

                token == "-b" || token == "--cookie" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected cookie value after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["Cookie"] = value.text
                    i += 2
                }

                token.startsWith("-b") && token.length > 2 -> {
                    val value = token.substring(2)
                    if (value.isBlank()) {
                        warn("Expected cookie value after -b.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["Cookie"] = value
                    i += 1
                }

                token.startsWith("--cookie=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected cookie value after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    headers["Cookie"] = value
                    i += 1
                }

                token == "-x" || token == "--proxy" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected proxy URL after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    proxyUrl = value.text
                    i += 2
                }

                token.startsWith("-x") && token.length > 2 -> {
                    val value = token.substring(2)
                    if (value.isBlank()) {
                        warn("Expected proxy URL after -x.", tokenStart)
                        i += 1
                        continue
                    }
                    proxyUrl = value
                    i += 1
                }

                token.startsWith("--proxy=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected proxy URL after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    proxyUrl = value
                    i += 1
                }

                token == "--proxy-user" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected proxy credentials after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    proxyUser = value.text
                    i += 2
                }

                token.startsWith("--proxy-user=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected proxy credentials after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    proxyUser = value
                    i += 1
                }

                token == "-c" || token == "--cookie-jar" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected cookie jar path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    cookieJarPath = value.text
                    i += 2
                }

                token.startsWith("-c") && token.length > 2 -> {
                    val value = token.substring(2)
                    if (value.isBlank()) {
                        warn("Expected cookie jar path after -c.", tokenStart)
                        i += 1
                        continue
                    }
                    cookieJarPath = value
                    i += 1
                }

                token.startsWith("--cookie-jar=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected cookie jar path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    cookieJarPath = value
                    i += 1
                }

                token == "--connect-timeout" || token == "-m" || token == "--max-time" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected timeout value after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    val millis = parseSeconds(value.text)
                    if (millis == null) {
                        warn("Invalid timeout value: ${value.text}.", value.start)
                        i += 2
                        continue
                    }
                    if (token == "--connect-timeout") {
                        connectTimeoutMillis = millis
                    } else {
                        maxTimeMillis = millis
                    }
                    i += 2
                }

                token.startsWith("--connect-timeout=") || token.startsWith("--max-time=") -> {
                    val value = token.substringAfter("=")
                    val millis = parseSeconds(value)
                    if (millis == null) {
                        warn("Invalid timeout value: $value.", tokenStart)
                        i += 1
                        continue
                    }
                    if (token.startsWith("--connect-timeout=")) {
                        connectTimeoutMillis = millis
                    } else {
                        maxTimeMillis = millis
                    }
                    i += 1
                }

                token == "--retry" || token == "--retry-delay" || token == "--retry-max-time" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected retry value after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    when (token) {
                        "--retry" -> {
                            retryCount = value.text.toIntOrNull()
                                ?: run {
                                    warn("Invalid retry count: ${value.text}.", value.start)
                                    null
                                }
                        }

                        "--retry-delay" -> {
                            retryDelayMillis = parseSeconds(value.text)
                                ?: run {
                                    warn("Invalid retry delay: ${value.text}.", value.start)
                                    null
                                }
                        }

                        else -> {
                            retryMaxTimeMillis = parseSeconds(value.text)
                                ?: run {
                                    warn("Invalid retry max time: ${value.text}.", value.start)
                                    null
                                }
                        }
                    }
                    i += 2
                }

                token.startsWith("--retry=") ||
                        token.startsWith("--retry-delay=") ||
                        token.startsWith("--retry-max-time=") -> {
                    val value = token.substringAfter("=")
                    when {
                        token.startsWith("--retry=") -> {
                            retryCount = value.toIntOrNull()
                                ?: run {
                                    warn("Invalid retry count: $value.", tokenStart)
                                    null
                                }
                        }

                        token.startsWith("--retry-delay=") -> {
                            retryDelayMillis = parseSeconds(value)
                                ?: run {
                                    warn("Invalid retry delay: $value.", tokenStart)
                                    null
                                }
                        }

                        else -> {
                            retryMaxTimeMillis = parseSeconds(value)
                                ?: run {
                                    warn("Invalid retry max time: $value.", tokenStart)
                                    null
                                }
                        }
                    }
                    i += 1
                }

                token == "--cacert" || token == "--cert" || token == "--key" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected file path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    when (token) {
                        "--cacert" -> caCertPath = value.text
                        "--cert" -> {
                            val (path, password) = splitPassword(value.text)
                            clientCertPath = path
                            clientCertPassword = password
                        }

                        "--key" -> {
                            val (path, password) = splitPassword(value.text)
                            clientKeyPath = path
                            clientKeyPassword = password
                        }
                    }
                    i += 2
                }

                token.startsWith("--cacert=") || token.startsWith("--cert=") || token.startsWith("--key=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected file path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    when {
                        token.startsWith("--cacert=") -> caCertPath = value
                        token.startsWith("--cert=") -> {
                            val (path, password) = splitPassword(value)
                            clientCertPath = path
                            clientCertPassword = password
                        }

                        else -> {
                            val (path, password) = splitPassword(value)
                            clientKeyPath = path
                            clientKeyPassword = password
                        }
                    }
                    i += 1
                }

                token == "--unix-socket" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected socket path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    unixSocketPath = value.text
                    warn("Unix socket is not supported yet: $token ${value.text}.", tokenStart)
                    i += 2
                }

                token.startsWith("--unix-socket=") -> {
                    val value = token.substringAfter("=")
                    if (value.isBlank()) {
                        warn("Expected socket path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    unixSocketPath = value
                    warn("Unix socket is not supported yet: $token $value.", tokenStart)
                    i += 1
                }

                token == "-K" || token == "--config" -> {
                    val value = tokens.getOrNull(i + 1)
                    if (value == null || value.text.isBlank()) {
                        warn("Expected config path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    val path = value.text
                    if (loadedConfigs.contains(path)) {
                        warn("Config file already loaded: $path.", tokenStart)
                        i += 2
                        continue
                    }
                    if (configLoader == null) {
                        warn("Curl config files are not supported yet: $token $path.", tokenStart)
                        i += 2
                        continue
                    }
                    val configText = configLoader(path)
                    if (configText == null) {
                        warn("Cannot read config file: $path.", tokenStart)
                        i += 2
                        continue
                    }
                    loadedConfigs += path
                    val configTokens = tokenizeConfig(configText)
                    if (configTokens.isNotEmpty()) {
                        tokens.addAll(i + 2, configTokens)
                    }
                    i += 2
                }

                token.startsWith("--config=") -> {
                    val path = token.substringAfter("=")
                    if (path.isBlank()) {
                        warn("Expected config path after $token.", tokenStart)
                        i += 1
                        continue
                    }
                    if (loadedConfigs.contains(path)) {
                        warn("Config file already loaded: $path.", tokenStart)
                        i += 1
                        continue
                    }
                    if (configLoader == null) {
                        warn("Curl config files are not supported yet: $token $path.", tokenStart)
                        i += 1
                        continue
                    }
                    val configText = configLoader(path)
                    if (configText == null) {
                        warn("Cannot read config file: $path.", tokenStart)
                        i += 1
                        continue
                    }
                    loadedConfigs += path
                    val configTokens = tokenizeConfig(configText)
                    if (configTokens.isNotEmpty()) {
                        tokens.addAll(i + 1, configTokens)
                    }
                    i += 1
                }

                token == "-L" || token == "--location" -> {
                    i += 1
                }

                token == "-k" || token == "--insecure" || token == "--compressed" -> {
                    i += 1
                }

                token.startsWith("-") -> {
                    i += 1
                }

                else -> {
                    if (looksLikeUrl(token)) {
                        url = token
                    }
                    i += 1
                }
            }
        }

        if (url.isNullOrBlank()) {
            val error = CurlParseError("Missing URL.", text.length)
            return CurlParseResult(null, error, warnings)
        }
        if (getQuery && bodyParts.isNotEmpty()) {
            val (mergedUrl, queryWarning) = applyGetQuery(url, bodyParts)
            url = mergedUrl
            if (queryWarning != null) {
                warn(queryWarning, 0)
            }
            bodyParts.clear()
        }
        if (method.isNullOrBlank()) {
            method = if (bodyParts.isNotEmpty() || formParts.isNotEmpty()) "POST" else "GET"
        }
        if (getQuery && !methodExplicit) {
            method = "GET"
        }
        val body = buildBody(bodyParts, formParts, headers)
        val responseTarget = resolveResponseTarget(url, outputPath, remoteNameRequested, warnings)
        val options =
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
        val spec = HttpRequestSpec(method.uppercase(), url, headers, body, version, responseTarget, options)
        return CurlParseResult(spec, null, warnings)
    }

    private fun addHeader(
        headers: MutableMap<String, String>,
        header: String,
    ): Boolean {
        val index = header.indexOf(':')
        if (index <= 0) return false
        val name = header.substring(0, index).trim()
        val value = header.substring(index + 1).trim()
        if (name.isNotEmpty()) {
            headers[name] = value
        }
        return name.isNotEmpty()
    }

    private fun toBodyValue(value: String): String =
        if (value.startsWith("@")) {
            "< " + value.removePrefix("@")
        } else {
            value
        }

    private fun basicAuthHeader(creds: String): String {
        val encoded = java.util.Base64.getEncoder().encodeToString(creds.toByteArray())
        return "Basic $encoded"
    }

    private fun buildBody(
        bodyParts: List<String>,
        formParts: List<FormPart>,
        headers: MutableMap<String, String>,
    ): RequestBodySpec? {
        if (formParts.isNotEmpty()) {
            val boundary = "ReqRunBoundary"
            headers.putIfAbsent("Content-Type", "multipart/form-data; boundary=$boundary")
            val lines = mutableListOf<String>()
            for (part in formParts) {
                lines += "--$boundary"
                val filename = part.filename?.let { "; filename=\"$it\"" }.orEmpty()
                lines += "Content-Disposition: form-data; name=\"${part.name}\"$filename"
                part.contentType?.let { lines += "Content-Type: $it" }
                lines += ""
                lines += if (part.isFile && part.filePath != null) "< ${part.filePath}" else part.value.orEmpty()
            }
            lines += "--$boundary--"
            val text = lines.joinToString("\n").ifBlank { return null }
            return TextBody(text)
        }
        val body = bodyParts.joinToString("\n").ifBlank { return null }
        return TextBody(body)
    }

    private fun resolveResponseTarget(
        url: String,
        outputPath: String?,
        remoteNameRequested: Boolean,
        warnings: MutableList<CurlParseWarning>,
    ): ResponseTarget? {
        val path =
            when {
                !outputPath.isNullOrBlank() -> outputPath
                remoteNameRequested -> {
                    val name = extractFileName(url)
                    if (name == null) {
                        warnings += CurlParseWarning("Cannot infer remote file name from URL.", 0)
                    }
                    name
                }

                else -> null
            } ?: return null
        return try {
            FileResponseTarget(java.nio.file.Path.of(path), append = false)
        } catch (_: Exception) {
            warnings += CurlParseWarning("Invalid output path: '$path'.", 0)
            null
        }
    }

    private fun extractFileName(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val path = uri.path ?: return null
            val name = path.substringAfterLast('/')
            name.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyGetQuery(
        url: String,
        bodyParts: List<String>,
    ): Pair<String, String?> {
        if (bodyParts.isEmpty()) return url to null
        val queryParts = bodyParts.filter { !it.startsWith("< ") }
        val hasFiles = queryParts.size != bodyParts.size
        val query = queryParts.joinToString("&").trim()
        if (query.isBlank()) {
            val message = if (hasFiles) "Cannot apply file uploads with --get." else null
            return url to message
        }
        return try {
            val uri = java.net.URI(url)
            val currentQuery = uri.rawQuery
            val combined = if (currentQuery.isNullOrBlank()) query else "$currentQuery&$query"
            val rebuilt =
                java.net.URI(
                    uri.scheme,
                    uri.authority,
                    uri.path,
                    combined,
                    uri.fragment,
                ).toString()
            rebuilt to if (hasFiles) "Cannot apply file uploads with --get." else null
        } catch (_: Exception) {
            val separator = if (url.contains("?")) "&" else "?"
            val rebuilt = url + separator + query
            rebuilt to if (hasFiles) "Cannot apply file uploads with --get." else null
        }
    }

    private fun appendOptions(
        sb: StringBuilder,
        options: RequestOptions,
    ) {
        val lines = mutableListOf<String>()
        options.proxyUrl?.let { lines += "# @reqrun.proxy $it" }
        options.proxyUser?.let { lines += "# @reqrun.proxy-user $it" }
        options.connectTimeoutMillis?.let { lines += "# @reqrun.connect-timeout ${formatSeconds(it)}" }
        options.maxTimeMillis?.let { lines += "# @reqrun.max-time ${formatSeconds(it)}" }
        options.retryCount?.let { lines += "# @reqrun.retry $it" }
        options.retryDelayMillis?.let { lines += "# @reqrun.retry-delay ${formatSeconds(it)}" }
        options.retryMaxTimeMillis?.let { lines += "# @reqrun.retry-max-time ${formatSeconds(it)}" }
        options.cookieJarPath?.let { lines += "# @reqrun.cookie-jar $it" }
        options.tls?.caCertPath?.let { lines += "# @reqrun.cacert $it" }
        options.tls?.clientCertPath?.let { path ->
            val suffix = options.tls.clientCertPassword?.let { ":$it" }.orEmpty()
            lines += "# @reqrun.cert $path$suffix"
        }
        options.tls?.clientKeyPath?.let { path ->
            val suffix = options.tls.clientKeyPassword?.let { ":$it" }.orEmpty()
            lines += "# @reqrun.key $path$suffix"
        }
        options.unixSocketPath?.let { lines += "# @reqrun.unix-socket $it" }
        if (lines.isEmpty()) return
        sb.append(lines.joinToString("\n")).append('\n')
    }

    private fun parseSeconds(value: String): Long? {
        val seconds = value.toDoubleOrNull() ?: return null
        if (seconds < 0.0) return null
        return (seconds * 1000.0).roundToLong()
    }

    private fun formatSeconds(millis: Long): String {
        val seconds = millis / 1000.0
        val text = seconds.toString()
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }

    private fun splitPassword(value: String): Pair<String, String?> {
        val index = value.lastIndexOf(':')
        if (index <= 0) return value to null
        val path = value.substring(0, index)
        val password = value.substring(index + 1).takeIf { it.isNotEmpty() }
        return path to password
    }

    private fun tokenizeConfig(content: String): List<Token> {
        val lines =
            content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    if (line.startsWith("-")) {
                        line
                    } else {
                        val eq = line.indexOf('=')
                        if (eq > 0) {
                            val key = line.substring(0, eq).trim()
                            val value = line.substring(eq + 1).trim()
                            "--$key $value"
                        } else {
                            line
                        }
                    }
                }.toList()
        if (lines.isEmpty()) return emptyList()
        val normalized = lines.joinToString(" ")
        return tokenizeWithRanges(normalized)
    }

    private fun parseFormPart(raw: String): FormPart? {
        val separator = raw.indexOf('=')
        if (separator <= 0) return null
        val name = raw.substring(0, separator)
        val valueWithOptions = raw.substring(separator + 1)
        val (value, options) = splitValueOptions(valueWithOptions)
        val filename = options["filename"]
        val contentType = options["type"]
        return if (value.startsWith("@")) {
            val path = value.removePrefix("@")
            val inferredName = filename ?: path.substringAfterLast('/')
            FormPart(
                name,
                value = null,
                isFile = true,
                filePath = path,
                filename = inferredName,
                contentType = contentType
            )
        } else {
            FormPart(
                name,
                value = value,
                isFile = false,
                filePath = null,
                filename = filename,
                contentType = contentType
            )
        }
    }

    private fun splitValueOptions(raw: String): Pair<String, Map<String, String>> {
        val segments = raw.split(';')
        if (segments.isEmpty()) return "" to emptyMap()
        val value = segments.first()
        if (segments.size == 1) return value to emptyMap()
        val options = linkedMapOf<String, String>()
        for (segment in segments.drop(1)) {
            val trimmed = segment.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val key = trimmed.substring(0, eq)
            val optValue = trimmed.substring(eq + 1).trim('"')
            options[key] = optValue
        }
        return value to options
    }

    private fun looksLikeUrl(value: String): Boolean = value.startsWith("http://") || value.startsWith("https://")

    private fun shellQuote(value: String): String {
        if (value.isEmpty()) return "''"
        val escaped = value.replace("'", "'\"'\"'")
        return "'$escaped'"
    }

    private fun formatVersion(version: java.net.http.HttpClient.Version): String =
        when (version) {
            java.net.http.HttpClient.Version.HTTP_1_1 -> "HTTP/1.1"
            java.net.http.HttpClient.Version.HTTP_2 -> "HTTP/2"
        }

    private fun tokenizeWithRanges(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escape = false
        var tokenStart = -1
        var pendingEmptyToken = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (!inSingle && ch == '\\') {
                val next = input.getOrNull(i + 1)
                if (next == '\n' || next == '\r') {
                    i += 1
                    if (next == '\r' && input.getOrNull(i + 1) == '\n') {
                        i += 1
                    }
                    i += 1
                    continue
                }
            }
            when {
                escape -> {
                    if (tokenStart == -1) tokenStart = i
                    pendingEmptyToken = false
                    sb.append(ch)
                    escape = false
                }

                inSingle -> {
                    if (ch == '\'') {
                        inSingle = false
                        if (sb.isEmpty()) {
                            pendingEmptyToken = true
                        }
                    } else {
                        pendingEmptyToken = false
                        if (tokenStart == -1) tokenStart = i
                        sb.append(ch)
                    }
                }

                inDouble -> {
                    when (ch) {
                        '"' -> {
                            inDouble = false
                            if (sb.isEmpty()) {
                                pendingEmptyToken = true
                            }
                        }

                        '\\' -> escape = true
                        else -> {
                            pendingEmptyToken = false
                            if (tokenStart == -1) tokenStart = i
                            sb.append(ch)
                        }
                    }
                }

                ch == '\'' -> {
                    if (tokenStart == -1) tokenStart = i
                    inSingle = true
                }

                ch == '"' -> {
                    if (tokenStart == -1) tokenStart = i
                    inDouble = true
                }

                ch == '\\' -> escape = true
                ch.isWhitespace() -> {
                    if (sb.isNotEmpty()) {
                        tokens += Token(sb.toString(), tokenStart, i)
                        sb.setLength(0)
                        tokenStart = -1
                    } else if (pendingEmptyToken) {
                        tokens += Token("", tokenStart, i)
                        tokenStart = -1
                    }
                    pendingEmptyToken = false
                }

                else -> {
                    pendingEmptyToken = false
                    if (tokenStart == -1) tokenStart = i
                    sb.append(ch)
                }
            }
            i += 1
        }
        if (sb.isNotEmpty()) {
            tokens += Token(sb.toString(), tokenStart, i)
        } else if (pendingEmptyToken) {
            tokens += Token("", tokenStart, i)
        }
        return tokens
    }

    private data class Token(
        val text: String,
        val start: Int,
        val end: Int,
    )

    private data class FormPart(
        val name: String,
        val value: String?,
        val isFile: Boolean,
        val filePath: String?,
        val filename: String?,
        val contentType: String?,
    )
}
