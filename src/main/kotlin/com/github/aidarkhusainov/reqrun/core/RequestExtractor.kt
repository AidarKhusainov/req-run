package com.github.aidarkhusainov.reqrun.core

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange

object RequestExtractor {
    data class RequestBlock(val text: String, val startOffset: Int)

    fun extract(editor: Editor): String? {
        val document = editor.document
        val selection = editor.selectionModel
        if (selection.hasSelection()) {
            val text = selection.selectedText?.takeIf { it.isNotBlank() }
            return text
        }

        val lineCount = document.lineCount
        if (lineCount == 0) {
            return null
        }

        val caretLine = editor.caretModel.logicalPosition.line
        val caretText = document.getText(TextRange(document.getLineStartOffset(caretLine), document.getLineEndOffset(caretLine)))
        val useBoundaryScan = caretText.isBlank() || isSeparator(caretText)
        val (startLine, endLine) = if (useBoundaryScan) {
            val start = findBoundary(document, caretLine, direction = -1)
            val end = findBoundary(document, caretLine, direction = 1)
            start to end
        } else {
            val requestLine = findRequestLine(document, caretLine) ?: return null
            val end = findEndLine(document, requestLine, caretLine)
            requestLine to end
        }

        val startOffset = document.getLineStartOffset(startLine)
        val endOffset = document.getLineEndOffset(endLine)
        val range = TextRange(startOffset, endOffset)
        val raw = document.getText(range)
        val block = raw
            .lineSequence()
            .filterNot { isSeparator(it) || isComment(it) }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        return block
    }

    fun extractAll(editor: Editor): List<RequestBlock> {
        val selection = editor.selectionModel
        val text = if (selection.hasSelection()) selection.selectedText else editor.document.text
        val baseOffset = if (selection.hasSelection()) selection.selectionStart else 0
        if (text.isNullOrBlank()) return emptyList()
        return extractAllFromText(text, baseOffset)
    }

    fun extractAllFromText(text: String, baseOffset: Int = 0): List<RequestBlock> {
        if (text.isBlank()) return emptyList()
        val blocks = mutableListOf<RequestBlock>()
        val separatorRegex = Regex("(?m)^\\s*###.*$")
        var cursor = 0
        for (match in separatorRegex.findAll(text)) {
            val blockText = text.substring(cursor, match.range.first)
            addBlock(blocks, blockText, baseOffset + cursor)
            var nextStart = match.range.last + 1
            if (nextStart < text.length && text[nextStart] == '\n') {
                nextStart++
            }
            cursor = nextStart
        }
        if (cursor <= text.length) {
            val blockText = text.substring(cursor)
            addBlock(blocks, blockText, baseOffset + cursor)
        }
        return blocks
    }

    fun extractFull(editor: Editor): String? {
        val document = editor.document
        val selection = editor.selectionModel
        if (selection.hasSelection()) {
            val text = selection.selectedText?.takeIf { it.isNotBlank() }
            return text
        }

        val lineCount = document.lineCount
        if (lineCount == 0) {
            return null
        }

        val caretLine = editor.caretModel.logicalPosition.line
        val requestLine = findRequestLine(document, caretLine) ?: return null
        val endLine = (findNextSeparator(document, requestLine + 1) ?: document.lineCount) - 1
        val startOffset = document.getLineStartOffset(requestLine)
        val endOffset = document.getLineEndOffset(endLine.coerceAtLeast(requestLine))
        val range = TextRange(startOffset, endOffset)
        val raw = document.getText(range)
        val block = raw
            .lineSequence()
            .filterNot { isSeparator(it) }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        return block
    }

    private fun findBoundary(document: com.intellij.openapi.editor.Document, from: Int, direction: Int): Int {
        var line = from
        while (line in 0 until document.lineCount) {
            val text = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            if ((text.isBlank() || isSeparator(text)) && line != from) break
            line += direction
        }
        // step back to last non-blank
        line -= direction
        return line.coerceIn(0, document.lineCount - 1)
    }

    private fun findRequestLine(document: com.intellij.openapi.editor.Document, from: Int): Int? {
        var line = from.coerceIn(0, document.lineCount - 1)
        while (line >= 0) {
            val text = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            if (isSeparator(text)) {
                return null
            }
            if (looksLikeRequestLine(text)) {
                return line
            }
            line -= 1
        }
        return null
    }

    private fun findEndLine(document: com.intellij.openapi.editor.Document, requestLine: Int, caretLine: Int): Int {
        val separatorLine = findNextSeparator(document, requestLine + 1)
        val blankLine = findNextBlank(document, requestLine + 1, separatorLine)
        val endLine = if (blankLine != null && caretLine <= blankLine) {
            blankLine - 1
        } else {
            (separatorLine ?: document.lineCount) - 1
        }
        return endLine.coerceAtLeast(requestLine)
    }

    private fun findNextSeparator(document: com.intellij.openapi.editor.Document, from: Int): Int? {
        var line = from
        while (line in 0 until document.lineCount) {
            val text = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            if (isSeparator(text)) return line
            line += 1
        }
        return null
    }

    private fun findNextBlank(document: com.intellij.openapi.editor.Document, from: Int, stopAt: Int?): Int? {
        var line = from
        val end = stopAt ?: document.lineCount
        while (line in 0 until end) {
            val text = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            if (text.isBlank()) return line
            line += 1
        }
        return null
    }

    private fun isSeparator(line: String): Boolean = line.trimStart().startsWith("###")

    private fun isComment(line: String): Boolean = line.trimStart().startsWith("#")

    private fun looksLikeRequestLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.matches(Regex("^[A-Za-z]+\\s+\\S+.*$"))
    }

    private fun addBlock(target: MutableList<RequestBlock>, blockText: String, startOffset: Int) {
        val trimmed = blockText.trimEnd('\n', '\r')
        if (trimmed.isNotBlank()) {
            target.add(RequestBlock(trimmed, startOffset))
        }
    }
}
