package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec

object CurlConverter {
    fun toCurl(spec: HttpRequestSpec): String {
        val parts = mutableListOf("curl", "-X", spec.method, shellQuote(spec.url))
        spec.headers.forEach { (name, value) ->
            parts += "-H"
            parts += shellQuote("$name: $value")
        }
        spec.body?.let {
            parts += "--data"
            parts += shellQuote(it)
        }
        return parts.joinToString(" ")
    }

    fun toHttp(spec: HttpRequestSpec): String {
        val sb = StringBuilder()
        sb.append(spec.method).append(' ').append(spec.url)
        if (spec.headers.isNotEmpty()) {
            sb.append('\n')
            spec.headers.forEach { (name, value) ->
                sb.append(name).append(": ").append(value).append('\n')
            }
            sb.setLength(sb.length - 1)
        }
        spec.body?.let {
            sb.append("\n\n").append(it)
        }
        return sb.toString()
    }

    fun fromCurl(text: String): HttpRequestSpec? {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null
        var i = 0
        if (tokens[0] == "curl") {
            i = 1
        }
        var method: String? = null
        var url: String? = null
        val headers = linkedMapOf<String, String>()
        val bodyParts = mutableListOf<String>()

        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token == "-X" || token == "--request" -> {
                    method = tokens.getOrNull(i + 1)
                    i += 2
                }
                token.startsWith("-X") && token.length > 2 -> {
                    method = token.substring(2)
                    i += 1
                }
                token.startsWith("--request=") -> {
                    method = token.substringAfter("=")
                    i += 1
                }
                token == "-H" || token == "--header" -> {
                    val header = tokens.getOrNull(i + 1)
                    if (header != null) {
                        addHeader(headers, header)
                    }
                    i += 2
                }
                token.startsWith("-H") && token.length > 2 -> {
                    addHeader(headers, token.substring(2))
                    i += 1
                }
                token.startsWith("--header=") -> {
                    addHeader(headers, token.substringAfter("="))
                    i += 1
                }
                token == "-d" || token == "--data" || token == "--data-raw" ||
                    token == "--data-binary" || token == "--data-urlencode" || token == "--data-ascii" -> {
                    val data = tokens.getOrNull(i + 1)
                    if (data != null) {
                        bodyParts += data
                    }
                    i += 2
                }
                token.startsWith("-d") && token.length > 2 -> {
                    bodyParts += token.substring(2)
                    i += 1
                }
                token.startsWith("--data=") -> {
                    bodyParts += token.substringAfter("=")
                    i += 1
                }
                token == "--url" -> {
                    url = tokens.getOrNull(i + 1)
                    i += 2
                }
                token.startsWith("--url=") -> {
                    url = token.substringAfter("=")
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

        if (url.isNullOrBlank()) return null
        if (method.isNullOrBlank()) {
            method = if (bodyParts.isNotEmpty()) "POST" else "GET"
        }
        val body = bodyParts.joinToString("\n").ifBlank { null }
        return HttpRequestSpec(method.uppercase(), url, headers, body)
    }

    private fun addHeader(headers: MutableMap<String, String>, header: String) {
        val index = header.indexOf(':')
        if (index <= 0) return
        val name = header.substring(0, index).trim()
        val value = header.substring(index + 1).trim()
        if (name.isNotEmpty()) {
            headers[name] = value
        }
    }

    private fun looksLikeUrl(value: String): Boolean {
        return value.startsWith("http://") || value.startsWith("https://")
    }

    private fun shellQuote(value: String): String {
        if (value.isEmpty()) return "''"
        val escaped = value.replace("'", "'\"'\"'")
        return "'$escaped'"
    }

    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escape = false
        for (ch in input) {
            when {
                escape -> {
                    sb.append(ch)
                    escape = false
                }
                inSingle -> {
                    if (ch == '\'') {
                        inSingle = false
                    } else {
                        sb.append(ch)
                    }
                }
                inDouble -> {
                    when (ch) {
                        '"' -> inDouble = false
                        '\\' -> escape = true
                        else -> sb.append(ch)
                    }
                }
                ch == '\'' -> inSingle = true
                ch == '"' -> inDouble = true
                ch == '\\' -> escape = true
                ch.isWhitespace() -> {
                    if (sb.isNotEmpty()) {
                        tokens += sb.toString()
                        sb.setLength(0)
                    }
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            tokens += sb.toString()
        }
        return tokens
    }
}
