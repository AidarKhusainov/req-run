package com.github.aidarkhusainov.reqrun.lang

import com.github.aidarkhusainov.reqrun.icons.ReqRunIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object ReqRunFileType : LanguageFileType(ReqRunLanguage) {

    override fun getName(): String = "ReqRun HTTP"

    override fun getDescription(): String = "Simple HTTP request files"

    override fun getDefaultExtension(): String = "http"

    override fun getIcon(): Icon = ReqRunIcons.Api
}
