package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.*
import org.junit.Assert.*
import org.junit.Test
import java.net.http.HttpClient
import java.nio.file.Path

class CurlConverterTest {
    @Test
    fun `toCurl includes headers and body`() {
        val raw =
            """
            POST https://httpbin.org/post
            Accept: application/json
            Content-Type: text/xml

            <ping>ReqRun</ping>
            """.trimIndent()
        val spec = HttpRequestParser.parse(raw)
        val curl = CurlConverter.toCurl(spec!!)
        assertEquals(
            "curl -X POST 'https://httpbin.org/post' -H 'Accept: application/json' -H 'Content-Type: text/xml' --data '<ping>ReqRun</ping>'",
            curl,
        )
    }

    @Test
    fun `fromCurl parses method headers and body`() {
        val curl = "curl -X POST 'https://example.com/api' -H 'Accept: application/json' --data '{\"a\":1}'"
        val spec = CurlConverter.fromCurl(curl)
        assertNotNull(spec)
        assertEquals("POST", spec?.method)
        assertEquals("https://example.com/api", spec?.url)
        assertEquals("application/json", spec?.headers?.get("Accept"))
        assertEquals("{\"a\":1}", spec?.body?.preview)
    }

    @Test
    fun `fromCurl returns null without url`() {
        val curl = "curl -X POST -H 'Accept: application/json'"
        val spec = CurlConverter.fromCurl(curl)
        assertNull(spec)
    }

    @Test
    fun `toHttp renders body with blank line separator`() {
        val curl = "curl --request POST --url https://example.com -H 'X-Test: v' --data 'line1\nline2'"
        val spec = CurlConverter.fromCurl(curl)
        val http = CurlConverter.toHttp(spec!!)
        assertEquals(
            "POST https://example.com\nX-Test: v\n\nline1\nline2",
            http,
        )
    }

    @Test
    fun `toCurl escapes single quotes`() {
        val spec =
            HttpRequestSpec(
                method = "POST",
                url = "https://example.com/it's",
                headers = mapOf("X-Name" to "O'Brian"),
                body = TextBody("value='1'"),
            )

        val curl = CurlConverter.toCurl(spec)

        assertEquals(
            "curl -X POST 'https://example.com/it'\"'\"'s' -H 'X-Name: O'\"'\"'Brian' --data 'value='\"'\"'1'\"'\"''",
            curl,
        )
    }

    @Test
    fun `toCurl omits data when body is null`() {
        val spec =
            HttpRequestSpec(
                method = "GET",
                url = "https://example.com",
                headers = emptyMap<String, String>(),
                body = null as RequestBodySpec?,
            )

        val curl = CurlConverter.toCurl(spec)

        assertEquals("curl -X GET 'https://example.com'", curl)
    }

    @Test
    fun `toCurl uses data-binary with file body`() {
        val path = Path.of("tmp", "payload.bin")
        val spec =
            HttpRequestSpec(
                method = "POST",
                url = "https://example.com/upload",
                headers = emptyMap<String, String>(),
                body =
                    CompositeBody(
                        preview = "< ./payload.bin",
                        parts = listOf(BodyPart.File(path)),
                    ),
            )

        val curl = CurlConverter.toCurl(spec)

        assertEquals(
            "curl -X POST 'https://example.com/upload' --data-binary '@$path'",
            curl,
        )
    }

    @Test
    fun `fromCurl parses request flag variants and headers`() {
        val curl = "curl --request=PATCH -H 'Header: v' --header='X-Test: ok' https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("PATCH", spec?.method)
        assertEquals("https://example.com", spec?.url)
        assertEquals("v", spec?.headers?.get("Header"))
        assertEquals("ok", spec?.headers?.get("X-Test"))
    }

    @Test
    fun `fromCurl joins multiple data flags`() {
        val curl = "curl -d one --data two --data-raw three https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("POST", spec?.method)
        assertEquals("one\ntwo\nthree", spec?.body?.preview)
    }

    @Test
    fun `fromCurl parses quoted and escaped tokens`() {
        val curl = "curl -XPOST \"https://example.com/a b\" -d \"a=1&b=2\""

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("POST", spec?.method)
        assertEquals("https://example.com/a b", spec?.url)
        assertEquals("a=1&b=2", spec?.body?.preview)
    }

    @Test
    fun `fromCurl supports line continuations with headers`() {
        val curl =
            "curl https://example.com \\\n" +
                    "  --header 'Accept: application/json' \\\n" +
                    "  --header 'X-Test: v'"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("GET", spec?.method)
        assertEquals("https://example.com", spec?.url)
        assertEquals("application/json", spec?.headers?.get("Accept"))
        assertEquals("v", spec?.headers?.get("X-Test"))
    }

    @Test
    fun `fromCurl parses data file reference`() {
        val curl = "curl --data-binary @payload.bin https://example.com/upload"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("POST", spec?.method)
        assertEquals("< payload.bin", spec?.body?.preview)
    }

    @Test
    fun `fromCurl parses header flag variants`() {
        val curl = "curl -HAccept: application/json --header=User-Agent: test https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("application/json", spec?.headers?.get("Accept"))
        assertEquals("test", spec?.headers?.get("User-Agent"))
    }

    @Test
    fun `fromCurl ignores location and compressed flags`() {
        val curl = "curl -L --compressed https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("GET", spec?.method)
        assertEquals("https://example.com", spec?.url)
    }

    @Test
    fun `fromCurl applies get flag to query`() {
        val curl = "curl -G --data 'a=1&b=2' https://example.com/path"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("GET", spec?.method)
        assertEquals("https://example.com/path?a=1&b=2", spec?.url)
        assertEquals(null, spec?.body)
    }

    @Test
    fun `fromCurl maps output flag to response target`() {
        val curl = "curl -o out.txt https://example.com/file"

        val spec = CurlConverter.fromCurlDetailed(curl).spec

        assertEquals("out.txt", spec?.responseTarget?.path?.toString())
        assertEquals(
            "GET https://example.com/file\n\n> out.txt",
            CurlConverter.toHttp(spec!!),
        )
    }

    @Test
    fun `fromCurl maps remote name to response target`() {
        val curl = "curl -O https://example.com/files/report.txt"

        val spec = CurlConverter.fromCurlDetailed(curl).spec

        assertEquals("report.txt", spec?.responseTarget?.path?.toString())
    }

    @Test
    fun `fromCurl maps agent referer and cookie headers`() {
        val curl = "curl -A 'ua' -e 'https://ref.example' -b 'a=1' https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("ua", spec?.headers?.get("User-Agent"))
        assertEquals("https://ref.example", spec?.headers?.get("Referer"))
        assertEquals("a=1", spec?.headers?.get("Cookie"))
    }

    @Test
    fun `fromCurl parses timeouts and retry settings`() {
        val curl =
            "curl --connect-timeout 2 --max-time 5 --retry 3 --retry-delay 0.5 --retry-max-time 10 https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals(2000L, spec?.options?.connectTimeoutMillis)
        assertEquals(5000L, spec?.options?.maxTimeMillis)
        assertEquals(3, spec?.options?.retryCount)
        assertEquals(500L, spec?.options?.retryDelayMillis)
        assertEquals(10000L, spec?.options?.retryMaxTimeMillis)
    }

    @Test
    fun `fromCurl parses tls settings`() {
        val curl = "curl --cacert ca.pem --cert client.p12:pass --key key.pem:secret https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        val tls = spec?.options?.tls
        assertEquals("ca.pem", tls?.caCertPath)
        assertEquals("client.p12", tls?.clientCertPath)
        assertEquals("pass", tls?.clientCertPassword)
        assertEquals("key.pem", tls?.clientKeyPath)
        assertEquals("secret", tls?.clientKeyPassword)
    }

    @Test
    fun `fromCurl parses cookie jar`() {
        val curl = "curl -c cookies.txt https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("cookies.txt", spec?.options?.cookieJarPath)
    }

    @Test
    fun `toHttp includes reqrun options directives`() {
        val curl = "curl --connect-timeout 1 --max-time 2 -x http://proxy.local https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertTrue(CurlConverter.toHttp(spec!!).startsWith("# @reqrun.proxy http://proxy.local"))
    }

    @Test
    fun `fromCurl reads config file`() {
        val curl = "curl -K config.txt https://example.com"

        val result =
            CurlConverter.fromCurlDetailed(curl) { _ ->
                "--header 'X-Test: v'"
            }

        assertEquals("v", result.spec?.headers?.get("X-Test"))
    }

    @Test
    fun `fromCurl warns on unsupported proxy`() {
        val curl = "curl -x http://proxy.local https://example.com"

        val result = CurlConverter.fromCurlDetailed(curl)

        assertEquals("https://example.com", result.spec?.url)
        assertEquals("http://proxy.local", result.spec?.options?.proxyUrl)
        assertEquals(true, result.warnings.isEmpty())
    }

    @Test
    fun `fromCurl adds basic auth header`() {
        val curl = "curl -u user:pass https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("GET", spec?.method)
        assertEquals("Basic dXNlcjpwYXNz", spec?.headers?.get("Authorization"))
    }

    @Test
    fun `fromCurl sets head method`() {
        val curl = "curl -I https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("HEAD", spec?.method)
    }

    @Test
    fun `fromCurl builds multipart body from form`() {
        val curl = "curl -F 'note=hi' -F 'file=@payload.bin' https://example.com/upload"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("POST", spec?.method)
        assertEquals("multipart/form-data; boundary=ReqRunBoundary", spec?.headers?.get("Content-Type"))
        val body = spec?.body?.preview ?: ""
        assertTrue(body.contains("Content-Disposition: form-data; name=\"note\""))
        assertTrue(body.contains("hi"))
        assertTrue(body.contains("Content-Disposition: form-data; name=\"file\"; filename=\"payload.bin\""))
        assertTrue(body.contains("< payload.bin"))
        assertTrue(body.contains("--ReqRunBoundary--"))
    }

    @Test
    fun `fromCurl supports form options`() {
        val curl =
            "curl -F 'file=@payload.bin;type=application/octet-stream;filename=custom.bin' https://example.com/upload"

        val spec = CurlConverter.fromCurl(curl)

        val body = spec?.body?.preview ?: ""
        assertTrue(body.contains("Content-Disposition: form-data; name=\"file\"; filename=\"custom.bin\""))
        assertTrue(body.contains("Content-Type: application/octet-stream"))
    }

    @Test
    fun `fromCurlDetailed reports header error but returns spec`() {
        val curl = "curl https://example.com -H BadHeader"

        val result = CurlConverter.fromCurlDetailed(curl)

        assertEquals("https://example.com", result.spec?.url)
        assertEquals("Invalid header format: 'BadHeader'.", result.warnings.firstOrNull()?.message)
    }

    @Test
    fun `toHttp omits extra blank lines when no headers or body`() {
        val spec =
            HttpRequestSpec(
                method = "GET",
                url = "https://example.com",
                headers = emptyMap<String, String>(),
                body = null as RequestBodySpec?,
            )

        val http = CurlConverter.toHttp(spec)

        assertEquals("GET https://example.com", http)
    }

    @Test
    fun `fromCurl captures http2 flag`() {
        val curl = "curl --http2 https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals(HttpClient.Version.HTTP_2, spec?.version)
    }

    @Test
    fun `fromCurl captures http2 prior knowledge flag`() {
        val curl = "curl --http2-prior-knowledge https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals(HttpClient.Version.HTTP_2, spec?.version)
    }

    @Test
    fun `fromCurl captures http1_1 flag`() {
        val curl = "curl --http1.1 https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals(HttpClient.Version.HTTP_1_1, spec?.version)
    }

    @Test
    fun `fromCurl last http flag wins`() {
        val curl = "curl --http2 --http1.1 https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals(HttpClient.Version.HTTP_1_1, spec?.version)
    }

    @Test
    fun `fromCurl ignores unknown http flag`() {
        val curl = "curl --http3 https://example.com"

        val spec = CurlConverter.fromCurl(curl)

        assertEquals(null, spec?.version)
    }

    @Test
    fun `toHttp includes version when provided`() {
        val spec =
            HttpRequestSpec(
                method = "GET",
                url = "https://example.com",
                headers = emptyMap<String, String>(),
                body = null as RequestBodySpec?,
                version = HttpClient.Version.HTTP_2,
            )

        val http = CurlConverter.toHttp(spec)

        assertEquals("GET https://example.com HTTP/2", http)
    }
}
