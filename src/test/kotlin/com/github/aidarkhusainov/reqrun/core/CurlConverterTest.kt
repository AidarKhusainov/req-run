package com.github.aidarkhusainov.reqrun.core

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
}
