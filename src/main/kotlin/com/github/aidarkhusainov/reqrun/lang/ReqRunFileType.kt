package com.github.aidarkhusainov.reqrun.lang

import com.github.aidarkhusainov.reqrun.icons.ReqRunIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class ReqRunFileType : LanguageFileType(ReqRunLanguage) {
    companion object {
        val INSTANCE: ReqRunFileType = ReqRunFileType()
    }

    override fun getName(): String = "ReqRun HTTP"

    override fun getDescription(): String = "Simple HTTP request files"

    override fun getDefaultExtension(): String = "http"

    override fun getIcon(): Icon = ReqRunIcons.Api
}
