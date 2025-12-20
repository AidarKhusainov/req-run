package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import org.junit.Assert.*
import org.junit.Test

class HttpRequestParserTest {
    @Test
    fun `parse returns null for blank input`() {
        assertNull(HttpRequestParser.parse(""))
        assertNull(HttpRequestParser.parse("   \n  "))
    }

    @Test
    fun `parse returns null for invalid request line`() {
        assertNull(HttpRequestParser.parse("GET"))
        assertNull(HttpRequestParser.parse("GET    "))
        assertNull(HttpRequestParser.parse("\nPOST\n"))
    }

    @Test
    fun `parse normalizes method and trims request line`() {
        val spec = HttpRequestParser.parse("  get   https://example.com  ")
        assertEquals(
            HttpRequestSpec("GET", "https://example.com", emptyMap(), null),
            spec
        )
    }

    @Test
    fun `parse headers and body`() {
        val raw = """
            POST https://example.com/api
            Content-Type: application/json
            X-Test: a:b

            {"a":1}
            line2
        """.trimIndent()

        val spec = HttpRequestParser.parse(raw)
        assertEquals("POST", spec?.method)
        assertEquals("https://example.com/api", spec?.url)
        assertEquals("application/json", spec?.headers?.get("Content-Type"))
        assertEquals("a:b", spec?.headers?.get("X-Test"))
        assertEquals("{\"a\":1}\nline2", spec?.body)
    }

    @Test
    fun `parse ignores malformed headers`() {
        val raw = """
            GET https://example.com
            BadHeader
            : missing-name
            Good: value
        """.trimIndent()

        val spec = HttpRequestParser.parse(raw)
        assertEquals(1, spec?.headers?.size)
        assertEquals("value", spec?.headers?.get("Good"))
    }

    @Test
    fun `parse keeps empty header values`() {
        val raw = """
            GET https://example.com
            Empty: 
        """.trimIndent()

        val spec = HttpRequestParser.parse(raw)
        assertEquals("", spec?.headers?.get("Empty"))
    }

    @Test
    fun `parse body is null when no body separator`() {
        val raw = """
            POST https://example.com
            Content-Type: application/json
        """.trimIndent()

        val spec = HttpRequestParser.parse(raw)
        assertNull(spec?.body)
    }

    @Test
    fun `parse body is null when separator is empty`() {
        val raw = """
            POST https://example.com

        """.trimIndent()

        val spec = HttpRequestParser.parse(raw)
        assertNull(spec?.body)
    }

    @Test
    fun `parse treats header-looking lines after separator as body`() {
        val raw = """
            POST https://example.com

            Header: not-a-header
        """.trimIndent()

        val spec = HttpRequestParser.parse(raw)
        assertEquals("Header: not-a-header", spec?.body)
    }

    @Test
    fun `parse preserves blank lines in body`() {
        val raw = """
            POST https://example.com

            line1

            line3
        """.trimIndent()

        val spec = HttpRequestParser.parse(raw)
        assertEquals("line1\n\nline3", spec?.body)
        assertTrue(spec?.body?.contains("\n\n") == true)
    }
}
