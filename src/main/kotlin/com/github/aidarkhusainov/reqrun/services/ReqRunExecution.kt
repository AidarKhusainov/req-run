package com.github.aidarkhusainov.reqrun.services

import com.github.aidarkhusainov.reqrun.model.HttpRequestSpec
import com.github.aidarkhusainov.reqrun.model.HttpResponsePayload
import com.intellij.openapi.vfs.VirtualFile
import java.time.Instant
import java.util.UUID

data class ReqRunExecution(
    val id: UUID = UUID.randomUUID(),
    val startedAt: Instant = Instant.now(),
    val request: HttpRequestSpec,
    val response: HttpResponsePayload?,
    val error: String? = null,
    val source: ReqRunRequestSource? = null,
)

data class ReqRunRequestSource(
    val file: VirtualFile,
    val offset: Int,
)
