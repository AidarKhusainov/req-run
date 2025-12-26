package com.github.aidarkhusainov.reqrun.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup

class ReqRunImportGroup : DefaultActionGroup("Import", true) {
    init {
        templatePresentation.icon = AllIcons.Actions.Download
        add(PasteCurlAction())
    }
}
