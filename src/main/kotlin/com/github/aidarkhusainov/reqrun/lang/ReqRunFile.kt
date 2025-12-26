package com.github.aidarkhusainov.reqrun.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class ReqRunFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ReqRunLanguage) {
    override fun getFileType(): FileType = ReqRunFileType.INSTANCE

    override fun toString(): String = "ReqRun File"
}
