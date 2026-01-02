package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class OkHttpReqRunClient(
    private val client: OkHttpClient
) : ReqRunHttpClient {
    private data class JsonFormatResult(
        val formatted: String?,
        val error: String?
    )

    override fun execute(
        request: HttpRequestSpec,
        indicator: ProgressIndicator?,
        requestTimeout: Duration,
    ): HttpResponsePayload {
        val startedAt = System.nanoTime()
        val deadline = startedAt + requestTimeout.toNanos()
        val body = request.body?.toRequestBody(null)
        val builder = Request.Builder()
            .url(request.url)
            .method(request.method, body)
        request.headers.forEach { (name, value) ->
            builder.header(name, value)
        }

        val call = client.newCall(builder.build())
        val future = enqueue(call)
        while (true) {
            if (indicator?.isCanceled == true) {
                call.cancel()
                throw ProcessCanceledException()
            }
            if (System.nanoTime() >= deadline) {
                call.cancel()
                throw TimeoutException("Request timed out after ${requestTimeout.seconds}s")
            }
            try {
                val response = future.get(200, TimeUnit.MILLISECONDS)
                response.use {
                    val duration = (System.nanoTime() - startedAt) / 1_000_000
                    val status = "${formatVersion(it.protocol)} ${it.code} ${reasonPhrase(it.code)}"
                    val responseBody = it.body?.string().orEmpty()
                    val jsonFormat = formatJsonBody(responseBody, it.header("Content-Type"))
                    val formattedHtml = formatHtmlBody(responseBody, it.header("Content-Type"))
                    val formattedXml = formatXmlBody(responseBody, it.header("Content-Type"))
                    return HttpResponsePayload(
                        statusLine = status,
                        headers = it.headers.toMultimap(),
                        body = responseBody,
                        durationMillis = duration,
                        formattedBody = jsonFormat.formatted,
                        jsonFormatError = jsonFormat.error,
                        formattedHtml = formattedHtml,
                        formattedXml = formattedXml,
                    )
                }
            } catch (e: TimeoutException) {
                continue
            } catch (e: CancellationException) {
                throw ProcessCanceledException()
            } catch (e: InterruptedException) {
                call.cancel()
                Thread.currentThread().interrupt()
                throw ProcessCanceledException()
            } catch (e: ExecutionException) {
                val cause = e.cause
                if (cause is IOException) throw cause
                throw cause ?: e
            }
        }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        client.cache?.close()
    }

    private fun enqueue(call: Call): CompletableFuture<Response> {
        val future = CompletableFuture<Response>()
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                future.complete(response)
            }
        })
        return future
    }

    private fun formatVersion(protocol: Protocol): String =
        when (protocol) {
            Protocol.HTTP_1_0 -> "HTTP/1.0"
            Protocol.HTTP_1_1 -> "HTTP/1.1"
            Protocol.HTTP_2, Protocol.H2_PRIOR_KNOWLEDGE -> "HTTP/2"
            else -> "HTTP/1.1"
        }

    // Minimal reason map for common codes; rest left empty to avoid misleading text.
    private fun reasonPhrase(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        304 -> "Not Modified"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        422 -> "Unprocessable Entity"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> ""
    }

    private fun formatJsonBody(body: String, contentType: String?): JsonFormatResult {
        if (body.isBlank()) return JsonFormatResult(null, null)
        if (!isJsonContentType(contentType) && !looksLikeJson(body)) return JsonFormatResult(null, null)
        return try {
            val parsed = JsonParser.parseString(body)
            val formatted = GsonBuilder().setPrettyPrinting().create().toJson(parsed)
            JsonFormatResult(formatted, null)
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "Invalid JSON"
            JsonFormatResult(null, message)
        }
    }

    private fun isJsonContentType(contentType: String?): Boolean {
        val normalized = contentType?.lowercase() ?: return false
        return normalized.contains("application/json") || normalized.contains("+json")
    }

    private fun looksLikeJson(body: String): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun formatHtmlBody(body: String, contentType: String?): String? {
        if (body.isBlank()) return null
        if (!isHtmlContentType(contentType) && !looksLikeHtml(body)) return null
        return try {
            formatMarkup(body, isHtml = true)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatXmlBody(body: String, contentType: String?): String? {
        if (body.isBlank()) return null
        if (!isXmlContentType(contentType) && !looksLikeXml(body)) return null
        return try {
            formatMarkup(body, isHtml = false)
        } catch (_: Exception) {
            null
        }
    }

    private fun isHtmlContentType(contentType: String?): Boolean {
        val normalized = contentType?.lowercase() ?: return false
        return normalized.contains("text/html")
    }

    private fun isXmlContentType(contentType: String?): Boolean {
        val normalized = contentType?.lowercase() ?: return false
        return normalized.contains("application/xml") ||
            normalized.contains("text/xml") ||
            normalized.contains("+xml")
    }

    private fun looksLikeHtml(body: String): Boolean {
        val trimmed = body.trimStart().lowercase()
        return trimmed.startsWith("<!doctype") ||
            trimmed.startsWith("<html") ||
            (trimmed.startsWith("<") && trimmed.contains("<head"))
    }

    private fun looksLikeXml(body: String): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<")
    }

    private fun formatMarkup(body: String, isHtml: Boolean): String {
        val withBreaks = body.replace("><", ">\n<")
        val lines = withBreaks.split('\n')
        val sb = StringBuilder(withBreaks.length + 64)
        var indent = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val isClosing = trimmed.startsWith("</")
            val isDoctype = trimmed.startsWith("<!") || trimmed.startsWith("<?")
            val hasInlineClose = trimmed.indexOf("</") > 0
            val isSelfClosing = trimmed.endsWith("/>") || (isHtml && isHtmlVoidTag(trimmed))
            if (isClosing) {
                indent = (indent - 1).coerceAtLeast(0)
            }
            repeat(indent) { sb.append("  ") }
            sb.append(trimmed).append('\n')
            if (!isClosing && !isDoctype && !isSelfClosing && !hasInlineClose) {
                indent++
            }
        }
        return sb.toString().trimEnd()
    }

    private fun isHtmlVoidTag(line: String): Boolean {
        val name = extractTagName(line) ?: return false
        return name in setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
            "meta", "param", "source", "track", "wbr"
        )
    }

    private fun extractTagName(line: String): String? {
        val start = line.indexOf('<')
        if (start == -1) return null
        var i = start + 1
        while (i < line.length && (line[i] == '/' || line[i].isWhitespace())) {
            i++
        }
        val nameStart = i
        while (i < line.length && (line[i].isLetterOrDigit() || line[i] == ':' || line[i] == '_' || line[i] == '-')) {
            i++
        }
        if (i == nameStart) return null
        return line.substring(nameStart, i).lowercase()
    }
}
