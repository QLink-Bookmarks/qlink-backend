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
)
