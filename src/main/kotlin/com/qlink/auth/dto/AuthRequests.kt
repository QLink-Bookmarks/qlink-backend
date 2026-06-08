@file:Suppress("ktlint:standard:filename")

package com.qlink.auth.dto

import kotlinx.serialization.Serializable

enum class AuthPlatform {
    WEB,
    NATIVE,
}

@Serializable
data class SignInRequest(
    val provider: String,
    val token: String,
    val platform: AuthPlatform,
)
