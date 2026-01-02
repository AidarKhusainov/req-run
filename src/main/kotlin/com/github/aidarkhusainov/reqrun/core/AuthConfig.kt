package com.github.aidarkhusainov.reqrun.core

enum class AuthType {
    STATIC
}

enum class AuthScheme {
    BEARER,
    BASIC,
    API_KEY
}

data class AuthConfig(
    val type: AuthType,
    val scheme: AuthScheme,
    val token: String?,
    val username: String?,
    val password: String?,
    val header: String?,
)
