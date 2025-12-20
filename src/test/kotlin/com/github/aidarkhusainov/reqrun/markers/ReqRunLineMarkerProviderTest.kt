package com.github.aidarkhusainov.reqrun.markers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReqRunLineMarkerProviderTest : BasePlatformTestCase() {
    fun testMarkersOnlyForRequestLines() {
        val text = """
            GET https://a
            Header: v

            POST https://b
            ###
            PUT https://c
        """.trimIndent()
        myFixture.configureByText("test.http", text)
        val provider = ReqRunLineMarkerProvider()

        val doc = myFixture.editor.document
        val file = myFixture.file

        assertNotNull(markerAtLine(provider, file, doc, 0))
        assertNull(markerAtLine(provider, file, doc, 1))
        assertNull(markerAtLine(provider, file, doc, 3))
        assertNull(markerAtLine(provider, file, doc, 4))
        assertNotNull(markerAtLine(provider, file, doc, 5))
    }

    fun testNoMarkersInNonReqRunFile() {
        myFixture.configureByText("test.txt", "GET https://a")
        val provider = ReqRunLineMarkerProvider()

        val doc = myFixture.editor.document
        val file = myFixture.file

        assertNull(markerAtLine(provider, file, doc, 0))
    }

    private fun markerAtLine(
        provider: ReqRunLineMarkerProvider,
        file: com.intellij.psi.PsiFile,
        doc: com.intellij.openapi.editor.Document,
        line: Int
    ): LineMarkerInfo<*>? {
        val lineStart = doc.getLineStartOffset(line)
        val element = file.findElementAt(lineStart) ?: return null
        return provider.getLineMarkerInfo(element)
    }
}
