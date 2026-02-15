package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.github.aidarkhusainov.reqrun.model.RequestOptions
import com.github.aidarkhusainov.reqrun.model.TlsOptions
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
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
import java.security.PrivateKey
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*

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
        val options = request.options
        val retryCount = options.retryCount?.coerceAtLeast(0) ?: 0
        val maxAttempts = 1 + retryCount
        val retryDelayMillis = options.retryDelayMillis?.coerceAtLeast(0) ?: 0
        val startedAt = System.nanoTime()
        val maxTimeMillis = options.maxTimeMillis?.coerceAtLeast(0)
        val retryMaxTimeMillis = options.retryMaxTimeMillis?.coerceAtLeast(0)
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxAttempts) {
            val remaining = remainingMillis(startedAt, maxTimeMillis)
            if (remaining != null && remaining <= 0L) {
                throw java.util.concurrent.TimeoutException("Request timed out after ${formatMillis(maxTimeMillis)}s")
            }
            val client = getClient(request)
            val timeout =
                remaining?.let { Duration.ofMillis(it) } ?: requestTimeout
            try {
                return client.execute(request, indicator, timeout)
            } catch (t: Throwable) {
                if (t is ProcessCanceledException) throw t
                lastError = t
                if (attempt >= maxAttempts - 1) throw t
                if (retryMaxTimeMillis != null) {
                    val retryRemaining = remainingMillis(startedAt, retryMaxTimeMillis)
                    if (retryRemaining != null && retryRemaining <= 0L) throw t
                }
                if (retryDelayMillis > 0) {
                    sleepWithCancellation(retryDelayMillis, indicator)
                }
            }
            attempt += 1
        }
        throw lastError ?: IllegalStateException("Request failed without error details.")
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
            val settings = buildClientSettings(request.options)
            val key = ClientKey(sdkHome, resolveProtocolMode(request), settings)
            val cached = cachedClients[key]
            if (cached != null) return cached
            val newClient = OkHttpReqRunClient(buildOkHttpClient(sdkHome, key.mode, settings))
            cachedClients[key] = newClient
            return newClient
        }
    }

    private fun buildOkHttpClient(
        sdkHome: String?,
        mode: ProtocolMode,
        settings: ClientSettings,
    ): OkHttpClient {
        val sslConfig = resolveSslConfig(sdkHome, settings.tls)
        val dispatcher = Dispatcher(httpExecutor)
        val builder =
            OkHttpClient
                .Builder()
                .dispatcher(dispatcher)
                .connectTimeout(Duration.ofMillis(settings.connectTimeoutMillis))
                .callTimeout(Duration.ofMillis(settings.callTimeoutMillis))
                .readTimeout(Duration.ofMillis(settings.readTimeoutMillis))
                .writeTimeout(Duration.ofMillis(settings.writeTimeoutMillis))
                .followRedirects(true)
                .followSslRedirects(true)
                .sslSocketFactory(sslConfig.context.socketFactory, sslConfig.trustManager)
                .protocols(mode.protocols)
        settings.cookieJarPath?.let { path ->
            builder.cookieJar(FileCookieJar(Path.of(path)))
        }
        applyProxySettings(builder, settings)
        return builder.build()
    }

    private fun applyProxySettings(
        builder: OkHttpClient.Builder,
        settings: ClientSettings,
    ) {
        settings.proxy?.let { proxy ->
            builder.proxy(proxy)
            settings.proxyAuth?.let { auth ->
                builder.proxyAuthenticator(
                    okhttp3.Authenticator { _, response ->
                        val credential = Credentials.basic(auth.username, auth.password)
                        response.request
                            .newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    },
                )
            }
            return
        }
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

    private fun resolveSslConfig(
        sdkHome: String?,
        tls: TlsOptions?,
    ): SslConfig {
        val fallbackManager = CertificateManager.getInstance()
        val fallback = fallbackManager.sslContext
        val fallbackTrust = fallbackManager.trustManager
        val keyManagers =
            if (tls != null) {
                buildKeyManagers(tls)
            } else {
                null
            }
        val trustManager =
            if (tls?.caCertPath != null) {
                buildTrustManager(tls.caCertPath) ?: fallbackTrust
            } else {
                loadSdkTrustManager(sdkHome, fallbackTrust)
            }
        val context = SSLContext.getInstance("TLS")
        context.init(keyManagers, arrayOf(trustManager), null)
        return SslConfig(context, trustManager)
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
        val settings: ClientSettings,
    )

    private data class ClientSettings(
        val proxy: Proxy?,
        val proxyAuth: ProxyAuth?,
        val connectTimeoutMillis: Long,
        val callTimeoutMillis: Long,
        val readTimeoutMillis: Long,
        val writeTimeoutMillis: Long,
        val cookieJarPath: String?,
        val tls: TlsOptions?,
    )

    private data class ProxyAuth(
        val username: String,
        val password: String,
    )

    private class FileCookieJar(
        private val path: Path,
    ) : CookieJar {
        private val log = logger<FileCookieJar>()
        private val lock = Any()
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(
            url: HttpUrl,
            cookies: List<Cookie>,
        ) {
            synchronized(lock) {
                for (cookie in cookies) {
                    this.cookies.removeIf {
                        it.name == cookie.name &&
                                it.domain == cookie.domain &&
                                it.path == cookie.path
                    }
                    this.cookies += cookie
                }
                writeCookies()
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            synchronized(lock) {
                cookies.filter { it.matches(url) }
            }

        private fun writeCookies() {
            try {
                val parent = path.parent
                if (parent != null) {
                    Files.createDirectories(parent)
                }
                val sb = StringBuilder()
                sb.append("# Netscape HTTP Cookie File\n")
                for (cookie in cookies) {
                    val includeSubdomains = if (cookie.hostOnly) "FALSE" else "TRUE"
                    val secure = if (cookie.secure) "TRUE" else "FALSE"
                    val expires = if (cookie.persistent) cookie.expiresAt / 1000 else 0
                    sb.append(cookie.domain)
                        .append('\t')
                        .append(includeSubdomains)
                        .append('\t')
                        .append(cookie.path)
                        .append('\t')
                        .append(secure)
                        .append('\t')
                        .append(expires)
                        .append('\t')
                        .append(cookie.name)
                        .append('\t')
                        .append(cookie.value)
                        .append('\n')
                }
                Files.writeString(path, sb.toString())
            } catch (t: Throwable) {
                log.warn("ReqRun: failed to write cookie jar $path", t)
            }
        }
    }

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

    private fun buildClientSettings(options: RequestOptions): ClientSettings {
        val connectMillis = (options.connectTimeoutMillis ?: connectTimeout.toMillis()).coerceAtLeast(1)
        val callMillis = (options.maxTimeMillis ?: requestTimeout.toMillis()).coerceAtLeast(1)
        val proxy = options.proxyUrl?.let { parseProxy(it) }
        val proxyAuth = options.proxyUser?.let { parseProxyAuth(it) }
        if (options.unixSocketPath != null) {
            throw IllegalArgumentException("Unix socket is not supported yet.")
        }
        return ClientSettings(
            proxy = proxy,
            proxyAuth = proxyAuth,
            connectTimeoutMillis = connectMillis,
            callTimeoutMillis = callMillis,
            readTimeoutMillis = callMillis,
            writeTimeoutMillis = callMillis,
            cookieJarPath = options.cookieJarPath,
            tls = options.tls,
        )
    }

    private fun parseProxy(value: String): Proxy {
        val normalized =
            if (value.contains("://")) {
                value
            } else {
                "http://$value"
            }
        val uri = URI(normalized)
        val host = uri.host ?: throw IllegalArgumentException("Invalid proxy URL: $value")
        val port =
            if (uri.port != -1) {
                uri.port
            } else {
                if (uri.scheme == "https") 443 else 80
            }
        val type =
            when (uri.scheme?.lowercase()) {
                "socks", "socks5" -> Proxy.Type.SOCKS
                else -> Proxy.Type.HTTP
            }
        return Proxy(type, InetSocketAddress(host, port))
    }

    private fun parseProxyAuth(value: String): ProxyAuth {
        val index = value.indexOf(':')
        return if (index == -1) {
            ProxyAuth(value, "")
        } else {
            ProxyAuth(value.substring(0, index), value.substring(index + 1))
        }
    }

    private fun remainingMillis(
        startedAt: Long,
        maxTimeMillis: Long?,
    ): Long? {
        if (maxTimeMillis == null) return null
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000
        return maxTimeMillis - elapsed
    }

    private fun sleepWithCancellation(
        delayMillis: Long,
        indicator: ProgressIndicator?,
    ) {
        val deadline = System.nanoTime() + delayMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            if (indicator?.isCanceled == true) {
                throw ProcessCanceledException()
            }
            Thread.sleep(50)
        }
    }

    private fun formatMillis(millis: Long?): String {
        if (millis == null) return ""
        val seconds = millis / 1000.0
        val text = seconds.toString()
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }

    private fun buildTrustManager(path: String): X509TrustManager? {
        val certs = loadCertificates(Path.of(path))
        if (certs.isEmpty()) return null
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        certs.forEachIndexed { index, cert ->
            keyStore.setCertificateEntry("ca-$index", cert)
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
    }

    private fun buildKeyManagers(tls: TlsOptions): Array<KeyManager>? {
        val certPath = tls.clientCertPath ?: return null
        val keyPath = tls.clientKeyPath
        val password = (tls.clientKeyPassword ?: tls.clientCertPassword).orEmpty()
        if (keyPath == null && isPkcs12(certPath)) {
            val keyStore = KeyStore.getInstance("PKCS12")
            Files.newInputStream(Path.of(certPath)).use { input ->
                keyStore.load(input, password.toCharArray())
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password.toCharArray())
            return kmf.keyManagers
        }
        val certs = loadCertificates(Path.of(certPath))
        if (certs.isEmpty()) {
            throw IllegalArgumentException("No certificates found in $certPath")
        }
        val privateKey =
            keyPath?.let { loadPrivateKey(Path.of(it)) }
                ?: throw IllegalArgumentException("Private key is required for client certificate.")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("client", privateKey, password.toCharArray(), certs.toTypedArray())
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password.toCharArray())
        return kmf.keyManagers
    }

    private fun loadCertificates(path: Path): List<java.security.cert.Certificate> {
        if (!Files.isRegularFile(path)) {
            throw IllegalArgumentException("Certificate file not found: $path")
        }
        val factory = java.security.cert.CertificateFactory.getInstance("X.509")
        Files.newInputStream(path).use { input ->
            return factory.generateCertificates(input).toList()
        }
    }

    private fun loadPrivateKey(path: Path): PrivateKey {
        if (!Files.isRegularFile(path)) {
            throw IllegalArgumentException("Private key file not found: $path")
        }
        val content = Files.readString(path)
        if (content.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
            throw IllegalArgumentException("Encrypted private keys are not supported.")
        }
        val pem =
            when {
                content.contains("BEGIN PRIVATE KEY") ->
                    content.substringAfter("BEGIN PRIVATE KEY").substringBefore("END PRIVATE KEY")

                else -> throw IllegalArgumentException("Unsupported private key format.")
            }
        val keyBytes = java.util.Base64.getMimeDecoder().decode(pem)
        val spec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
        val algorithms = listOf("RSA", "EC", "DSA")
        for (algorithm in algorithms) {
            try {
                val factory = java.security.KeyFactory.getInstance(algorithm)
                return factory.generatePrivate(spec)
            } catch (_: Exception) {
                continue
            }
        }
        throw IllegalArgumentException("Unsupported private key algorithm.")
    }

    private fun isPkcs12(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".p12") || lower.endsWith(".pfx")
    }

    private fun loadSdkTrustManager(
        sdkHome: String?,
        fallbackTrust: X509TrustManager,
    ): X509TrustManager {
        if (sdkHome.isNullOrBlank()) return fallbackTrust
        val cacertsPath = Path.of(sdkHome, "lib", "security", "cacerts")
        if (!Files.isRegularFile(cacertsPath)) return fallbackTrust
        return try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            Files.newInputStream(cacertsPath).use { input ->
                keyStore.load(input, "changeit".toCharArray())
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: fallbackTrust
        } catch (t: Throwable) {
            log.warn("ReqRun: failed to load project SDK truststore from $cacertsPath, using IDE SSL context", t)
            fallbackTrust
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
