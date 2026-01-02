package com.github.aidarkhusainov.reqrun.model

import java.net.http.HttpClient

data class HttpRequestSpec(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
    val version: HttpClient.Version? = null,
) {
    constructor(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ) : this(method, url, headers, body, null)
}

data class HttpResponsePayload(
    val statusLine: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val durationMillis: Long,
)
