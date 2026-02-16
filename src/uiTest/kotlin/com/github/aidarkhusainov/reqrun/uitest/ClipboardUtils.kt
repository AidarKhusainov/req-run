package com.github.aidarkhusainov.reqrun.uitest

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

object ClipboardUtils {
    fun setText(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }

    fun getText(): String {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        return clipboard.getContents(null)?.getTransferData(DataFlavor.stringFlavor) as? String ?: ""
    }

    fun clear() {
        setText("")
    }
}
