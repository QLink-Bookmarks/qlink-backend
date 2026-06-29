@file:Suppress("ktlint:standard:filename")

package com.qlink.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class SignInResponse(
    val accessToken: String,
    val refreshToken: String,
    val allowsPrivacy: Boolean,
    val allowsAiUsage: Boolean,
)

@Serializable
data class ConnectAuthProviderResponse(
    val id: Long,
)
