package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CurlConverterTest {
    @Test
    fun `toCurl includes headers and body`() {
        val raw = """
            POST https://httpbin.org/post
            Accept: application/json
            Content-Type: text/xml

            <ping>ReqRun</ping>
        """.trimIndent()
        val spec = HttpRequestParser.parse(raw)
        val curl = CurlConverter.toCurl(spec!!)
        assertEquals(
            "curl -X POST 'https://httpbin.org/post' -H 'Accept: application/json' -H 'Content-Type: text/xml' --data '<ping>ReqRun</ping>'",
            curl
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
        assertEquals("{\"a\":1}", spec?.body)
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
            http
        )
    }

    @Test
    fun `toCurl escapes single quotes`() {
        val spec = HttpRequestSpec(
            method = "POST",
            url = "https://example.com/it's",
            headers = mapOf("X-Name" to "O'Brian"),
            body = "value='1'"
        )

        val curl = CurlConverter.toCurl(spec)

        assertEquals(
            "curl -X POST 'https://example.com/it'\"'\"'s' -H 'X-Name: O'\"'\"'Brian' --data 'value='\"'\"'1'\"'\"''",
            curl
        )
    }

    @Test
    fun `toCurl omits data when body is null`() {
        val spec = HttpRequestSpec(
            method = "GET",
            url = "https://example.com",
            headers = emptyMap(),
            body = null
        )

        val curl = CurlConverter.toCurl(spec)

        assertEquals("curl -X GET 'https://example.com'", curl)
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
        assertEquals("one\ntwo\nthree", spec?.body)
    }

    @Test
    fun `fromCurl parses quoted and escaped tokens`() {
        val curl = "curl -XPOST \"https://example.com/a b\" -d \"a=1&b=2\""

        val spec = CurlConverter.fromCurl(curl)

        assertEquals("POST", spec?.method)
        assertEquals("https://example.com/a b", spec?.url)
        assertEquals("a=1&b=2", spec?.body)
    }

    @Test
    fun `toHttp omits extra blank lines when no headers or body`() {
        val spec = HttpRequestSpec(
            method = "GET",
            url = "https://example.com",
            headers = emptyMap(),
            body = null
        )

        val http = CurlConverter.toHttp(spec)

        assertEquals("GET https://example.com", http)
    }
}
