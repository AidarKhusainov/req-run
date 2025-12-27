package com.github.aidarkhusainov.reqrun.markers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil

class ReqRunLineMarkerProviderTest : BasePlatformTestCase() {
    fun testMarkersOnlyForRequestLines() {
        val text = """
            GET https://a
            Header: v

            POST https://b
            ### Case1
            PUT https://c
        """.trimIndent()
        myFixture.configureByText("test.http", text)
        val provider = ReqRunLineMarkerProvider()

        val doc = myFixture.editor.document
        val file = myFixture.file
        val markers = collectMarkers(provider, file)

        assertNotNull(markerAtLine(markers, doc, 0))
        assertNull(markerAtLine(markers, doc, 1))
        assertNull(markerAtLine(markers, doc, 3))
        assertNull(markerAtLine(markers, doc, 4))
        assertNotNull(markerAtLine(markers, doc, 5))
    }

    fun testNoMarkersInNonReqRunFile() {
        myFixture.configureByText("test.txt", "GET https://a")
        val provider = ReqRunLineMarkerProvider()

        val doc = myFixture.editor.document
        val file = myFixture.file
        val markers = collectMarkers(provider, file)

        assertNull(markerAtLine(markers, doc, 0))
    }

    fun testOnlyFirstRequestLineInBlockGetsMarker() {
        val text = """
            GET https://a
            POST https://b
        """.trimIndent()
        myFixture.configureByText("test.http", text)
        val provider = ReqRunLineMarkerProvider()

        val doc = myFixture.editor.document
        val markers = collectMarkers(provider, myFixture.file)

        assertNotNull(markerAtLine(markers, doc, 0))
        assertNull(markerAtLine(markers, doc, 1))
    }

    fun testNoMarkersForUnsupportedMethods() {
        val text = "CONNECT https://example.com"
        myFixture.configureByText("test.http", text)
        val provider = ReqRunLineMarkerProvider()

        val doc = myFixture.editor.document
        val markers = collectMarkers(provider, myFixture.file)

        assertNull(markerAtLine(markers, doc, 0))
    }

    fun testMarkerRangeStartsAtMethodToken() {
        val text = "   GET https://example.com"
        myFixture.configureByText("test.http", text)
        val provider = ReqRunLineMarkerProvider()

        val markers = collectMarkers(provider, myFixture.file)
        val marker = markers.singleOrNull()

        assertNotNull(marker)
    }

    private fun collectMarkers(
        provider: ReqRunLineMarkerProvider,
        file: com.intellij.psi.PsiFile
    ): List<LineMarkerInfo<*>> {
        val elements = PsiTreeUtil.collectElements(file, PsiElementFilter { true }).toMutableList()
        val result = mutableListOf<LineMarkerInfo<*>>()
        provider.collectSlowLineMarkers(elements, result)
        return result
    }

    private fun markerAtLine(
        markers: List<LineMarkerInfo<*>>,
        doc: com.intellij.openapi.editor.Document,
        line: Int
    ): LineMarkerInfo<*>? {
        return markers.firstOrNull { doc.getLineNumber(it.startOffset) == line }
    }
}
