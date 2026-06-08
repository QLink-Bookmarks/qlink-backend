@file:Suppress("ktlint:standard:filename")

package com.qlink.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
)
