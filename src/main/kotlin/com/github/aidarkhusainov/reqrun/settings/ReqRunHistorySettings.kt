package com.github.aidarkhusainov.reqrun.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ReqRunHistorySettings",
    storages = [Storage("reqrun.xml")],
)
@Service(Service.Level.APP)
class ReqRunHistorySettings : PersistentStateComponent<ReqRunHistorySettings.State> {
    data class State(
        var shortenHistoryUrls: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
