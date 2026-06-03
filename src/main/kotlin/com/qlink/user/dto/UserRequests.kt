@file:Suppress("ktlint:standard:filename")

package com.qlink.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateMySettingsRequest(
    val theme: String? = null,
    val accent: String? = null,
    val allowsReminder: Boolean? = null,
    val defaultProviderId: Long? = null,
    val defaultModelId: Long? = null,
)
