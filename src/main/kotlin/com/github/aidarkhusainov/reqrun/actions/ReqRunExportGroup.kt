package com.github.aidarkhusainov.reqrun.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup

class ReqRunExportGroup : DefaultActionGroup("Export", true) {
    init {
        templatePresentation.icon = AllIcons.Actions.Copy
        add(CopyCurlAction())
    }
}
