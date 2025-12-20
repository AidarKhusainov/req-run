package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.components.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Service(Service.Level.PROJECT)
class ReqRunExecutor {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun execute(request: HttpRequestSpec): HttpResponsePayload {
        val startedAt = System.nanoTime()
        val bodyPublisher = request.body?.let { HttpRequest.BodyPublishers.ofString(it, StandardCharsets.UTF_8) }
            ?: HttpRequest.BodyPublishers.noBody()

        val builder = HttpRequest.newBuilder()
            .uri(URI(request.url))
            .method(request.method, bodyPublisher)

        request.headers.forEach { (name, value) ->
            builder.header(name, value)
        }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        val duration = (System.nanoTime() - startedAt) / 1_000_000
        val status = "${formatVersion(response.version())} ${response.statusCode()} ${reasonPhrase(response.statusCode())}"
        return HttpResponsePayload(
            statusLine = status,
            headers = response.headers().map(),
            body = response.body(),
            durationMillis = duration,
        )
    }

    private fun formatVersion(version: HttpClient.Version): String =
        when (version) {
            HttpClient.Version.HTTP_1_1 -> "HTTP/1.1"
            HttpClient.Version.HTTP_2 -> "HTTP/2"
            else -> version.name
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
}
