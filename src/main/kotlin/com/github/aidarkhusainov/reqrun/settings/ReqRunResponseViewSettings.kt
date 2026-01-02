package com.github.aidarkhusainov.reqrun.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ReqRunResponseViewSettings",
    storages = [Storage("reqrun.xml")]
)
@Service(Service.Level.APP)
class ReqRunResponseViewSettings : PersistentStateComponent<ReqRunResponseViewSettings.State> {
    data class State(
        var showLineNumbers: Boolean = true,
        var showRequestMethod: Boolean = true,
        var foldHeadersByDefault: Boolean = true,
        var viewMode: ResponseViewMode = ResponseViewMode.AUTO
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}

enum class ResponseViewMode {
    AUTO,
    TEXT,
    JSON,
    XML,
    HTML
}
