package com.qlink.user.domain

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotBlank
import com.qlink.common.error.requireNotLessThan
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requiresEmoji
import kotlin.time.Instant

private const val MIN_USERNAME_LENGTH = 3
private const val MAX_USERNAME_LENGTH = 100
private const val MAX_NICKNAME_LENGTH = 50
private const val MAX_AVATAR_EMOJI_LENGTH = 20

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
    val allowsPrivacy: Boolean = false,
    val allowsAiUsage: Boolean = false,
    val defaultAiProviderId: Long? = null,
    val defaultModelId: Long? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        validateUsername(username)
        validateNickname(nickname)
        avatarEmoji?.let(::validateAvatarEmoji)
    }

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
            allowsPrivacy = allowsPrivacy,
            allowsAiUsage = allowsAiUsage,
            defaultAiProviderId = defaultAiProviderId ?: this.defaultAiProviderId,
            defaultModelId = defaultModelId ?: this.defaultModelId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun changeProfile(
        username: String,
        nickname: String,
        avatarUrl: String?,
        avatarEmoji: String?,
    ): User =
        User(
            id = id,
            username = username,
            nickname = nickname,
            role = role,
            avatarUrl = avatarUrl,
            avatarEmoji = avatarEmoji,
            theme = theme,
            accent = accent,
            allowsReminder = allowsReminder,
            allowsPrivacy = allowsPrivacy,
            allowsAiUsage = allowsAiUsage,
            defaultAiProviderId = defaultAiProviderId,
            defaultModelId = defaultModelId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun changeAgreements(
        allowsPrivacy: Boolean,
        allowsAiUsage: Boolean,
    ): User =
        User(
            id = id,
            username = username,
            nickname = nickname,
            role = role,
            avatarUrl = avatarUrl,
            avatarEmoji = avatarEmoji,
            theme = theme,
            accent = accent,
            allowsReminder = allowsReminder,
            allowsPrivacy = allowsPrivacy,
            allowsAiUsage = allowsAiUsage,
            defaultAiProviderId = defaultAiProviderId,
            defaultModelId = defaultModelId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun hasSameSettings(other: User): Boolean =
        theme == other.theme &&
            accent == other.accent &&
            allowsReminder == other.allowsReminder &&
            defaultAiProviderId == other.defaultAiProviderId &&
            defaultModelId == other.defaultModelId

    private fun validateUsername(username: String) {
        username.requireNotBlank(ErrorCode.USER_USERNAME_BLANK)
        username.requireNotLessThan(MIN_USERNAME_LENGTH, ErrorCode.USER_USERNAME_UNDER_MIN)
        username.requireNotOver(MAX_USERNAME_LENGTH, ErrorCode.USER_USERNAME_OVER_MAX)
    }

    private fun validateNickname(nickname: String) {
        nickname.requireNotBlank(ErrorCode.USER_NICKNAME_BLANK)
        nickname.requireNotOver(MAX_NICKNAME_LENGTH, ErrorCode.USER_NICKNAME_OVER_MAX)
    }

    private fun validateAvatarEmoji(avatarEmoji: String) {
        avatarEmoji.requireNotOver(MAX_AVATAR_EMOJI_LENGTH, ErrorCode.USER_AVATAR_EMOJI_OVER_MAX)
        avatarEmoji.requiresEmoji(ErrorCode.USER_AVATAR_EMOJI_INVALID)
    }
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
