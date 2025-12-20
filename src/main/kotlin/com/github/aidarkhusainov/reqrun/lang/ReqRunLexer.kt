package com.github.aidarkhusainov.reqrun.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class ReqRunLexer : LexerBase() {
    private val methodPattern =
        Regex("^\\s*(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\\s+\\S+", RegexOption.IGNORE_CASE)

    private var buffer: CharSequence = ""
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        if (tokenEnd >= endOffset) {
            tokenType = null
            return
        }

        tokenStart = tokenEnd
        val newlineIndex = buffer.indexOf('\n', tokenStart)
        val lineEnd = when {
            newlineIndex == -1 -> endOffset
            newlineIndex >= endOffset -> endOffset
            else -> newlineIndex + 1
        }
        val lineText = buffer.subSequence(tokenStart, lineEnd).toString()

        tokenType = if (methodPattern.containsMatchIn(lineText)) ReqRunTypes.METHOD else ReqRunTypes.TEXT
        tokenEnd = lineEnd
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset
}
