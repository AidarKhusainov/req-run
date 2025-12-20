package com.github.aidarkhusainov.reqrun.core

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange

object RequestExtractor {
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
        val startLine = findBoundary(document, caretLine, direction = -1)
        val endLine = findBoundary(document, caretLine, direction = 1)

        val startOffset = document.getLineStartOffset(startLine)
        val endOffset = document.getLineEndOffset(endLine)
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

    private fun isSeparator(line: String): Boolean = line.trim() == "###"
}
