package com.qlink.user.domain

import com.qlink.auth.domain.Role
import kotlin.time.Instant

class User(
    val id: Long? = null,
    val username: String,
    val nickname: String,
    val role: Role = Role.NORMAL,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarEmoji: String? = null,
    val theme: UserTheme = UserTheme.LIGHT,
    val accent: UserAccent = UserAccent.BLUE,
    val allowsReminder: Boolean = true,
    val defaultAiProviderId: Long? = null,
    val defaultModelId: Long? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)

enum class UserTheme(
    val code: String,
    val responseName: String,
) {
    DARK("D", "dark"),
    LIGHT("L", "light"),
    ;

    companion object {
        fun fromCode(code: String): UserTheme = entries.first { it.code == code }
    }
}

enum class UserAccent(
    val code: String,
    val responseName: String,
) {
    GRAY("G", "gray"),
    PINK("P", "pink"),
    BLUE("B", "blue"),
    ;

    companion object {
        fun fromCode(code: String): UserAccent = entries.first { it.code == code }
    }
}
