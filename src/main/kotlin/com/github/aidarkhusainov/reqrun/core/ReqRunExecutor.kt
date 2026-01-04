package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.net.ssl.CertificateManager
import okhttp3.*
import java.net.*
import java.net.Authenticator
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Service(Service.Level.PROJECT)
class ReqRunExecutor(
    private val project: Project,
) : Disposable {
    private val log = logger<ReqRunExecutor>()

    private val requestTimeout = Duration.ofSeconds(60)
    private val connectTimeout = Duration.ofSeconds(10)
    private val httpExecutor =
        ThreadPoolExecutor(
            0,
            HTTP_EXECUTOR_MAX_THREADS,
            HTTP_EXECUTOR_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(HTTP_EXECUTOR_QUEUE_CAPACITY),
            HttpClientThreadFactory(),
            ThreadPoolExecutor.AbortPolicy(),
        ).apply {
            allowCoreThreadTimeOut(true)
        }

    @Volatile
    private var cachedSdkHome: String? = null

    private val cachedClients = mutableMapOf<ClientKey, ReqRunHttpClient>()

    @Volatile
    private var clientOverride: ReqRunHttpClient? = null

    fun execute(
        request: HttpRequestSpec,
        indicator: ProgressIndicator? = null,
    ): HttpResponsePayload {
        val client = getClient(request)
        return client.execute(request, indicator, requestTimeout)
    }

    fun setClientOverride(client: ReqRunHttpClient?) {
        synchronized(this) {
            val previous = clientOverride
            if (previous != null && previous !== client) {
                previous.close()
            }
            clientOverride = client
        }
    }

    private fun getClient(request: HttpRequestSpec): ReqRunHttpClient {
        clientOverride?.let { return it }
        val sdkHome =
            ReadAction.compute<String?, RuntimeException> {
                if (project.isDisposed) null else ProjectRootManager.getInstance(project).projectSdk?.homePath
            }
        synchronized(this) {
            if (sdkHome != cachedSdkHome) {
                cachedClients.values.forEach { it.close() }
                cachedClients.clear()
                cachedSdkHome = sdkHome
            }
            val key = ClientKey(sdkHome, resolveProtocolMode(request))
            val cached = cachedClients[key]
            if (cached != null) return cached
            val newClient = OkHttpReqRunClient(buildOkHttpClient(sdkHome, key.mode))
            cachedClients[key] = newClient
            return newClient
        }
    }

    private fun buildOkHttpClient(
        sdkHome: String?,
        mode: ProtocolMode,
    ): OkHttpClient {
        val sslConfig = resolveSslConfig(sdkHome)
        val dispatcher = Dispatcher(httpExecutor)
        val builder =
            OkHttpClient
                .Builder()
                .dispatcher(dispatcher)
                .connectTimeout(connectTimeout)
                .callTimeout(requestTimeout)
                .readTimeout(requestTimeout)
                .writeTimeout(requestTimeout)
                .followRedirects(true)
                .followSslRedirects(true)
                .sslSocketFactory(sslConfig.context.socketFactory, sslConfig.trustManager)
                .protocols(mode.protocols)
        applyProxySettings(builder)
        return builder.build()
    }

    private fun applyProxySettings(builder: OkHttpClient.Builder) {
        try {
            val providerClass = Class.forName("com.intellij.util.net.JdkProxyProvider")
            val instance = providerClass.getMethod("getInstance").invoke(null)
            val proxySelector = providerClass.getMethod("getProxySelector").invoke(instance) as? ProxySelector
            val authenticator = providerClass.getMethod("getAuthenticator").invoke(instance) as? Authenticator
            if (proxySelector != null) builder.proxySelector(proxySelector)
            if (authenticator != null) builder.proxyAuthenticator(JavaNetProxyAuthenticator(authenticator))
        } catch (t: Throwable) {
            log.debug("ReqRun: proxy settings unavailable, using defaults", t)
        }
    }

    private fun resolveSslConfig(sdkHome: String?): SslConfig {
        val fallbackManager = CertificateManager.getInstance()
        val fallback = fallbackManager.sslContext
        val fallbackTrust = fallbackManager.trustManager
        if (sdkHome.isNullOrBlank()) return SslConfig(fallback, fallbackTrust)
        val cacertsPath = Path.of(sdkHome, "lib", "security", "cacerts")
        if (!Files.isRegularFile(cacertsPath)) return SslConfig(fallback, fallbackTrust)
        return try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            Files.newInputStream(cacertsPath).use { input ->
                keyStore.load(input, "changeit".toCharArray())
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            val trustManager =
                tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                    ?: fallbackTrust
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf(trustManager), null)
            SslConfig(context, trustManager)
        } catch (t: Throwable) {
            log.warn("ReqRun: failed to load project SDK truststore from $cacertsPath, using IDE SSL context", t)
            SslConfig(fallback, fallbackTrust)
        }
    }

    override fun dispose() {
        clientOverride?.close()
        cachedClients.values.forEach { it.close() }
        cachedClients.clear()
        cachedSdkHome = null
        clientOverride = null
        httpExecutor.shutdown()
        try {
            if (!httpExecutor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                httpExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            httpExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private class HttpClientThreadFactory : java.util.concurrent.ThreadFactory {
        private val counter = AtomicInteger()

        override fun newThread(r: Runnable): Thread =
            Thread(r, "ReqRun-HttpClient-${counter.incrementAndGet()}").apply {
                isDaemon = true
                contextClassLoader = null
            }
    }

    private companion object {
        private const val HTTP_EXECUTOR_MAX_THREADS = 2
        private const val HTTP_EXECUTOR_QUEUE_CAPACITY = 128
        private const val HTTP_EXECUTOR_KEEP_ALIVE_SECONDS = 30L
        private const val EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 3L
    }

    private data class SslConfig(
        val context: SSLContext,
        val trustManager: X509TrustManager,
    )

    private data class ClientKey(
        val sdkHome: String?,
        val mode: ProtocolMode,
    )

    private enum class ProtocolMode(
        val protocols: List<Protocol>,
    ) {
        HTTP_1_1(listOf(Protocol.HTTP_1_1)),
        HTTP_2(listOf(Protocol.HTTP_2)),
        H2_PRIOR_KNOWLEDGE(listOf(Protocol.H2_PRIOR_KNOWLEDGE)),
    }

    private fun resolveProtocolMode(request: HttpRequestSpec): ProtocolMode {
        val version = request.version ?: return ProtocolMode.HTTP_1_1
        return when (version) {
            java.net.http.HttpClient.Version.HTTP_1_1 -> ProtocolMode.HTTP_1_1
            java.net.http.HttpClient.Version.HTTP_2 -> {
                if (request.url.startsWith("http://")) ProtocolMode.H2_PRIOR_KNOWLEDGE else ProtocolMode.HTTP_2
            }
        }
    }

    private class JavaNetProxyAuthenticator(
        private val authenticator: Authenticator,
    ) : okhttp3.Authenticator {
        @Suppress("ReturnCount")
        override fun authenticate(
            route: Route?,
            response: Response,
        ): okhttp3.Request? {
            if (response.code != 407) return null
            val proxy = route?.proxy ?: return null
            val address = proxy.address() as? InetSocketAddress
            val host = address?.hostString ?: response.request.url.host
            val port = address?.port ?: response.request.url.port
            val passwordAuth =
                requestPasswordAuthentication(
                    host,
                    address,
                    port,
                    response.request.url.scheme,
                    response.request.url
                        .toUri()
                        .toURL(),
                ) ?: return null
            val credential =
                Credentials.basic(
                    passwordAuth.userName ?: return null,
                    String(passwordAuth.password ?: return null),
                )
            return response.request
                .newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }

        private fun requestPasswordAuthentication(
            host: String,
            address: InetSocketAddress?,
            port: Int,
            protocol: String?,
            url: URL,
        ): PasswordAuthentication? =
            try {
                val method =
                    Authenticator::class.java.getDeclaredMethod(
                        "requestPasswordAuthenticationInstance",
                        String::class.java,
                        java.net.InetAddress::class.java,
                        Int::class.javaPrimitiveType,
                        String::class.java,
                        String::class.java,
                        String::class.java,
                        URL::class.java,
                        Authenticator.RequestorType::class.java,
                    )
                method.isAccessible = true
                method.invoke(
                    authenticator,
                    host,
                    address?.address,
                    port,
                    protocol,
                    null,
                    null,
                    url,
                    Authenticator.RequestorType.PROXY,
                ) as? PasswordAuthentication
            } catch (_: Throwable) {
                null
            }
    }
}
