package com.github.aidarkhusainov.reqrun.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup

class NewRequestGroup : DefaultActionGroup("Add to HTTP Client...", true) {
    init {
        templatePresentation.icon = AllIcons.General.Add
        add(InsertHttpRequestTemplateAction("GET"))
        add(InsertHttpRequestTemplateAction("POST"))
        add(InsertFileUploadRequestTemplateAction())
        add(InsertHttpRequestTemplateAction("PUT"))
        add(InsertHttpRequestTemplateAction("PATCH"))
        add(InsertHttpRequestTemplateAction("DELETE"))
        addSeparator()
        add(AddEnvVariableAction(isPrivate = false))
        add(AddEnvVariableAction(isPrivate = true))
        addSeparator()
        add(AddAuthConfigAction(isPrivate = false))
        add(AddAuthConfigAction(isPrivate = true))
    }
}
