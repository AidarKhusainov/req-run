package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.net.ssl.CertificateManager
import java.net.Authenticator
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

@Service(Service.Level.PROJECT)
class ReqRunExecutor(private val project: Project) {
    private val log = logger<ReqRunExecutor>()

    @Volatile
    private var cachedSdkHome: String? = null

    @Volatile
    private var cachedClient: HttpClient? = null

    fun execute(request: HttpRequestSpec, indicator: ProgressIndicator? = null): HttpResponsePayload {
        val client = getClient()
        val startedAt = System.nanoTime()
        val bodyPublisher = request.body?.let { HttpRequest.BodyPublishers.ofString(it, StandardCharsets.UTF_8) }
            ?: HttpRequest.BodyPublishers.noBody()

        val builder = HttpRequest.newBuilder()
            .uri(URI(request.url))
            .method(request.method, bodyPublisher)

        request.headers.forEach { (name, value) ->
            builder.header(name, value)
        }
        val future = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        while (true) {
            if (indicator?.isCanceled == true) {
                future.cancel(true)
                throw ProcessCanceledException()
            }
            try {
                val response = future.get(200, TimeUnit.MILLISECONDS)
                val duration = (System.nanoTime() - startedAt) / 1_000_000
                val status =
                    "${formatVersion(response.version())} ${response.statusCode()} ${reasonPhrase(response.statusCode())}"
                return HttpResponsePayload(
                    statusLine = status,
                    headers = response.headers().map(),
                    body = response.body(),
                    durationMillis = duration,
                )
            } catch (e: TimeoutException) {
                continue
            } catch (e: CancellationException) {
                throw ProcessCanceledException()
            } catch (e: InterruptedException) {
                future.cancel(true)
                Thread.currentThread().interrupt()
                throw ProcessCanceledException()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
    }

    private fun getClient(): HttpClient {
        val sdkHome = ReadAction.compute<String?, RuntimeException> {
            if (project.isDisposed) null else ProjectRootManager.getInstance(project).projectSdk?.homePath
        }
        val cached = cachedClient
        if (cached != null && cachedSdkHome == sdkHome) return cached
        synchronized(this) {
            val recheck = cachedClient
            if (recheck != null && cachedSdkHome == sdkHome) return recheck
            val newClient = applyProxySettings(HttpClient.newBuilder())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .sslContext(resolveSslContext(sdkHome))
                .build()
            cachedSdkHome = sdkHome
            cachedClient = newClient
            return newClient
        }
    }

    private fun applyProxySettings(builder: HttpClient.Builder): HttpClient.Builder {
        return try {
            val providerClass = Class.forName("com.intellij.util.net.JdkProxyProvider")
            val instance = providerClass.getMethod("getInstance").invoke(null)
            val proxySelector = providerClass.getMethod("getProxySelector").invoke(instance) as? ProxySelector
            val authenticator = providerClass.getMethod("getAuthenticator").invoke(instance) as? Authenticator
            if (proxySelector != null) builder.proxy(proxySelector)
            if (authenticator != null) builder.authenticator(authenticator)
            builder
        } catch (t: Throwable) {
            log.debug("ReqRun: proxy settings unavailable, using defaults", t)
            builder
        }
    }

    private fun resolveSslContext(sdkHome: String?): SSLContext {
        val fallback = CertificateManager.getInstance().sslContext
        if (sdkHome.isNullOrBlank()) return fallback
        val cacertsPath = Path.of(sdkHome, "lib", "security", "cacerts")
        if (!Files.isRegularFile(cacertsPath)) return fallback
        return try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            Files.newInputStream(cacertsPath).use { input ->
                keyStore.load(input, "changeit".toCharArray())
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            val context = SSLContext.getInstance("TLS")
            context.init(null, tmf.trustManagers, null)
            context
        } catch (t: Throwable) {
            log.warn("ReqRun: failed to load project SDK truststore from $cacertsPath, using IDE SSL context", t)
            fallback
        }
    }

    private fun formatVersion(version: HttpClient.Version): String =
        when (version) {
            HttpClient.Version.HTTP_1_1 -> "HTTP/1.1"
            HttpClient.Version.HTTP_2 -> "HTTP/2"
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
