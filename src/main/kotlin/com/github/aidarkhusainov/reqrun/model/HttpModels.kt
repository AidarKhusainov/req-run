package com.github.aidarkhusainov.reqrun.model

import java.net.http.HttpClient
import java.nio.file.Path

data class HttpRequestSpec(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: RequestBodySpec?,
    val version: HttpClient.Version? = null,
    val responseTarget: ResponseTarget? = null,
) {
    constructor(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ) : this(method, url, headers, body?.let { TextBody(it) }, null, null)

    constructor(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        version: HttpClient.Version? = null,
    ) : this(method, url, headers, body?.let { TextBody(it) }, version, null)

    constructor(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: RequestBodySpec?,
    ) : this(method, url, headers, body, null, null)
}

sealed interface RequestBodySpec {
    val preview: String
}

data class TextBody(
    override val preview: String,
) : RequestBodySpec

data class CompositeBody(
    override val preview: String,
    val parts: List<BodyPart>,
) : RequestBodySpec

sealed interface BodyPart {
    data class Text(
        val text: String,
    ) : BodyPart

    data class File(
        val path: Path,
    ) : BodyPart
}

sealed interface ResponseTarget {
    val path: Path
    val append: Boolean
}

data class FileResponseTarget(
    override val path: Path,
    override val append: Boolean,
) : ResponseTarget

data class HttpResponsePayload(
    val statusLine: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val durationMillis: Long,
    val formattedBody: String? = null,
    val jsonFormatError: String? = null,
    val formattedHtml: String? = null,
    val formattedXml: String? = null,
    val savedBodyPath: String? = null,
    val savedBodyAppend: Boolean = false,
) {
    constructor(
        statusLine: String,
        headers: Map<String, List<String>>,
        body: String,
        durationMillis: Long,
    ) : this(
        statusLine = statusLine,
        headers = headers,
        body = body,
        durationMillis = durationMillis,
        formattedBody = null,
        jsonFormatError = null,
        formattedHtml = null,
        formattedXml = null,
        savedBodyPath = null,
        savedBodyAppend = false,
    )
}
