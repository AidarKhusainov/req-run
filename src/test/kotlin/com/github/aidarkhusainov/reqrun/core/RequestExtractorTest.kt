package com.github.aidarkhusainov.reqrun.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RequestExtractorTest : BasePlatformTestCase() {
    fun testExtractReturnsSelectionWhenPresent() {
        val text =
            """
            GET https://example.com
            Header: v
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        val start = editor.document.text.indexOf("GET")
        val end = editor.document.getLineEndOffset(0)
        editor.selectionModel.setSelection(start, end)

        val extracted = RequestExtractor.extract(editor)
        assertEquals("GET https://example.com", extracted)
    }

    fun testExtractUsesBlockWhenSelectionIsBlank() {
        val text = "\nGET https://example.com\nHeader: v"
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        val start = 0
        val end = editor.document.getLineEndOffset(0)
        editor.selectionModel.setSelection(start, end)

        val extracted = RequestExtractor.extract(editor)
        assertEquals("\nGET https://example.com\nHeader: v", extracted)
    }

    fun testExtractReturnsNullForEmptyDocument() {
        myFixture.configureByText("test.http", "")
        val editor = myFixture.editor
        assertNull(RequestExtractor.extract(editor))
    }

    fun testExtractBlockAroundCaretSeparatedByBlankLines() {
        val text =
            """
            GET https://a

            POST https://b
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        moveCaretTo(editor, "POST")

        val extracted = RequestExtractor.extract(editor)
        assertEquals("POST https://b", extracted)
    }

    fun testExtractTreatsSeparatorAndExcludesIt() {
        val text =
            """
            GET https://a
            ###
            POST https://b
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        moveCaretTo(editor, "POST")

        val extracted = RequestExtractor.extract(editor)
        assertEquals("POST https://b", extracted)
        assertTrue(extracted?.contains("###") == false)
    }

    fun testExtractTreatsSeparatorWithTitleAndExcludesIt() {
        val text =
            """
            GET https://a
            ### Case1
            POST https://b
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        moveCaretTo(editor, "POST")

        val extracted = RequestExtractor.extract(editor)
        assertEquals("POST https://b", extracted)
        assertTrue(extracted?.contains("###") == false)
    }

    fun testExtractReturnsHeadersOnlyWhenBodySeparatedByBlankLine() {
        val text =
            """
            POST https://example.com
            H1: v1

            body line
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        moveCaretTo(editor, "H1")

        val extracted = RequestExtractor.extract(editor)
        assertEquals("POST https://example.com\nH1: v1", extracted)
    }

    fun testExtractFullReturnsHeadersAndBody() {
        val text =
            """
            POST https://example.com
            H1: v1

            body line
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        moveCaretTo(editor, "H1")

        val extracted = RequestExtractor.extractFull(editor)
        assertEquals("POST https://example.com\nH1: v1\n\nbody line", extracted)
    }

    fun testExtractCombinesBlocksWhenCaretIsOnSeparator() {
        val text =
            """
            GET https://a
            ###
            POST https://b
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        moveCaretTo(editor, "###")

        val extracted = RequestExtractor.extract(editor)
        assertEquals("GET https://a\nPOST https://b", extracted)
    }

    fun testExtractIgnoresCommentLines() {
        val text =
            """
            # request comment
            GET https://example.com
            # header comment
            Header: v
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        moveCaretTo(editor, "Header")

        val extracted = RequestExtractor.extract(editor)
        assertEquals("GET https://example.com\nHeader: v", extracted)
    }

    fun testExtractIncludesBodyWhenCaretInBody() {
        val text =
            """
            POST https://example.com
            H1: v1

            line1
            line2
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        moveCaretTo(editor, "line2")

        val extracted = RequestExtractor.extract(editor)
        assertEquals("POST https://example.com\nH1: v1\n\nline1\nline2", extracted)
    }

    fun testExtractUsesHeadersWhenCaretOnBlankLineBetweenHeadersAndBody() {
        val text =
            """
            POST https://example.com
            H1: v1

            body line
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        val blankLineOffset = text.indexOf("\n\n") + 1
        editor.caretModel.moveToOffset(blankLineOffset)

        val extracted = RequestExtractor.extract(editor)
        assertEquals("POST https://example.com\nH1: v1\n\nbody line", extracted)
    }

    fun testExtractReturnsNullForBlankSelection() {
        val text = "   \nGET https://example.com"
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        editor.selectionModel.setSelection(0, 3)

        val extracted = RequestExtractor.extract(editor)
        assertNull(extracted)
    }

    fun testExtractAllFromTextSplitsBySeparator() {
        val text =
            """
            GET https://a
            ###
            POST https://b
            """.trimIndent()

        val blocks = RequestExtractor.extractAllFromText(text)

        assertEquals(2, blocks.size)
        assertEquals("GET https://a", blocks[0].text)
        assertEquals(0, blocks[0].startOffset)
        assertEquals("POST https://b", blocks[1].text)
        assertEquals(text.indexOf("POST"), blocks[1].startOffset)
    }

    fun testExtractAllUsesSelectionOffsets() {
        val text =
            """
            GET https://a
            ###
            POST https://b
            """.trimIndent()
        myFixture.configureByText("test.http", text)
        val editor = myFixture.editor
        val selectionStart = text.indexOf("POST")
        editor.selectionModel.setSelection(selectionStart, text.length)

        val blocks = RequestExtractor.extractAll(editor)

        assertEquals(1, blocks.size)
        assertEquals("POST https://b", blocks[0].text)
        assertEquals(selectionStart, blocks[0].startOffset)
    }

    fun testExtractAllFromTextSkipsEmptyBlocksAroundSeparators() {
        val text =
            """
            ###
            GET https://a
            ###
            ###
            POST https://b
            ###
            """.trimIndent()

        val blocks = RequestExtractor.extractAllFromText(text)

        assertEquals(2, blocks.size)
        assertEquals("GET https://a", blocks[0].text)
        assertEquals("POST https://b", blocks[1].text)
    }

    private fun moveCaretTo(
        editor: com.intellij.openapi.editor.Editor,
        token: String,
    ) {
        val offset = editor.document.text.indexOf(token)
        require(offset >= 0) { "Token not found in document: $token" }
        editor.caretModel.moveToOffset(offset)
    }
}
