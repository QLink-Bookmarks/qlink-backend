package com.qlink.user.domain

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import kotlin.time.Instant

class User(
    val id: Long? = null,
    val username: String,
    val nickname: String,
    val role: Role = Role.NORMAL,
    val avatarUrl: String? = null,
    val avatarEmoji: String? = null,
    val theme: UserTheme = UserTheme.LIGHT,
    val accent: UserAccent = UserAccent.GRAY,
    val allowsReminder: Boolean = true,
    val defaultAiProviderId: Long? = null,
    val defaultModelId: Long? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    fun changeSettings(
        theme: UserTheme?,
        accent: UserAccent?,
        allowsReminder: Boolean?,
        defaultAiProviderId: Long?,
        defaultModelId: Long?,
    ): User =
        User(
            id = id,
            username = username,
            nickname = nickname,
            role = role,
            avatarUrl = avatarUrl,
            avatarEmoji = avatarEmoji,
            theme = theme ?: this.theme,
            accent = accent ?: this.accent,
            allowsReminder = allowsReminder ?: this.allowsReminder,
            defaultAiProviderId = defaultAiProviderId ?: this.defaultAiProviderId,
            defaultModelId = defaultModelId ?: this.defaultModelId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun hasSameSettings(other: User): Boolean =
        theme == other.theme &&
            accent == other.accent &&
            allowsReminder == other.allowsReminder &&
            defaultAiProviderId == other.defaultAiProviderId &&
            defaultModelId == other.defaultModelId
}

enum class UserTheme(
    val code: String,
    val responseName: String,
) {
    DARK("D", "dark"),
    LIGHT("L", "light"),
    ;

    companion object {
        fun fromCode(code: String): UserTheme = entries.first { it.code == code }

        fun fromRequestName(requestName: String): UserTheme =
            entries.firstOrNull { it.responseName == requestName }
                ?: throw BusinessException(ErrorCode.USER_THEME_NOT_SUPPORTED)
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

        fun fromRequestName(requestName: String): UserAccent =
            entries.firstOrNull { it.responseName == requestName }
                ?: throw BusinessException(ErrorCode.USER_ACCENT_NOT_SUPPORTED)
    }
}
