package com.github.aidarkhusainov.reqrun.core

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.progress.ProgressIndicator
import java.time.Duration

interface ReqRunHttpClient {
    fun execute(
        request: HttpRequestSpec,
        indicator: ProgressIndicator?,
        requestTimeout: Duration,
    ): HttpResponsePayload

    fun close() = Unit
}
