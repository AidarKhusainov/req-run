package com.github.aidarkhusainov.reqrun.model

data class HttpRequestSpec(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

data class HttpResponsePayload(
    val statusLine: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val durationMillis: Long,
)
