@file:Suppress("ktlint:standard:filename")

package com.qlink.user.dto

import com.qlink.auth.domain.Role
import kotlinx.serialization.Serializable

@Serializable
data class GetMyProfileResponse(
    val id: Long,
    val username: String,
    val nickname: String,
    val role: Role,
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val allowsPrivacy: Boolean,
    val allowsAiUsage: Boolean,
)

@Serializable
data class GetMySettingsResponse(
    val display: UserDisplaySettingsResponse,
    val behavior: UserBehaviorSettingsResponse,
    val ai: UserAiSettingsResponse,
    val providers: List<UserAuthProviderResponse>,
)

@Serializable
data class UserAuthProviderResponse(
    val id: Long,
    val type: String,
)

@Serializable
data class UserDisplaySettingsResponse(
    val theme: String,
    val accent: String,
)

@Serializable
data class UserBehaviorSettingsResponse(
    val allowsReminderNotification: Boolean,
)

@Serializable
data class UserAiSettingsResponse(
    val defaultProvider: UserDefaultProviderResponse,
    val defaultModel: UserDefaultModelResponse,
)

@Serializable
data class UserDefaultProviderResponse(
    val id: Long?,
    val type: String?,
)

@Serializable
data class UserDefaultModelResponse(
    val id: Long?,
    val model: String?,
)
