package com.github.aidarkhusainov.reqrun.lang

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

object ReqRunTypes {
    val FILE: IFileElementType = IFileElementType(ReqRunLanguage)
    val METHOD: IElementType = ReqRunTokenType("METHOD")
    val TEXT: IElementType = ReqRunTokenType("TEXT")
    val COMMENT: IElementType = ReqRunTokenType("COMMENT")
}
