package com.github.aidarkhusainov.reqrun.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(name = "ReqRunProjectEnvPathSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ReqRunProjectEnvPathSettings : PersistentStateComponent<ReqRunProjectEnvPathSettings.State> {
    data class State(
        var useProjectPaths: Boolean = false,
        var sharedPath: String? = null,
        var privatePath: String? = null,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
