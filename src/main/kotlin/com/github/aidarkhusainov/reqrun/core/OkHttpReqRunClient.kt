package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
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
                    return HttpResponsePayload(
                        statusLine = status,
                        headers = it.headers.toMultimap(),
                        body = responseBody,
                        durationMillis = duration,
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
}
