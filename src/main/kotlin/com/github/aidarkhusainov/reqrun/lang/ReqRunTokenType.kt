package com.github.aidarkhusainov.reqrun.lang

import com.intellij.psi.tree.IElementType

class ReqRunTokenType(debugName: String) : IElementType(debugName, ReqRunLanguage) {
    override fun toString(): String = "ReqRunToken.${super.toString()}"
}
