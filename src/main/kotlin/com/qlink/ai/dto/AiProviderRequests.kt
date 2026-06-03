@file:Suppress("ktlint:standard:filename")

package com.qlink.ai.dto

import kotlinx.serialization.Serializable

@Serializable
data class PutAiUserProviderRequest(
    val providerId: Long,
    val apiKey: String,
)
