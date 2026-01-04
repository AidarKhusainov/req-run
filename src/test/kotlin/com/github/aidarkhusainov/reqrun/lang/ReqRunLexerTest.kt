package com.github.aidarkhusainov.reqrun.lang

import org.junit.Assert.assertEquals
import org.junit.Test

class ReqRunLexerTest {
    @Test
    fun `lexer tokenizes comments methods and text by line`() {
        val text =
            """
            # comment
            GET https://example.com
            Header: v
            """.trimIndent()

        val tokens = tokenize(text)

        assertEquals(listOf(ReqRunTypes.TEXT, ReqRunTypes.METHOD, ReqRunTypes.TEXT), tokens.map { it.first })
    }

    @Test
    fun `lexer treats blank lines as text`() {
        val text = "GET https://example.com\n\n# comment"

        val tokens = tokenize(text)

        assertEquals(listOf(ReqRunTypes.METHOD, ReqRunTypes.TEXT, ReqRunTypes.COMMENT), tokens.map { it.first })
        assertEquals("\n", tokens[1].second)
    }

    private fun tokenize(input: String): List<Pair<com.intellij.psi.tree.IElementType, String>> {
        val lexer = ReqRunLexer()
        lexer.start(input, 0, input.length, 0)
        val tokens = mutableListOf<Pair<com.intellij.psi.tree.IElementType, String>>()
        while (lexer.tokenType != null) {
            val text = input.substring(lexer.tokenStart, lexer.tokenEnd)
            tokens += lexer.tokenType!! to text
            lexer.advance()
        }
        return tokens
    }
}
