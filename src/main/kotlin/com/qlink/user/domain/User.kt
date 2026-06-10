package com.qlink.user.domain

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import kotlin.time.Instant

private const val MIN_USERNAME_LENGTH = 3
private const val MAX_USERNAME_LENGTH = 100
private const val MAX_NICKNAME_LENGTH = 50
private const val MAX_AVATAR_EMOJI_LENGTH = 20
private const val ZERO_WIDTH_JOINER = 0x200D
private const val VARIATION_SELECTOR_16 = 0xFE0F

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
        username.isNotBlank().requireTrue(ErrorCode.USER_USERNAME_BLANK)
        (username.length >= MIN_USERNAME_LENGTH).requireTrue(ErrorCode.USER_USERNAME_UNDER_MIN)
        username.requireNotOver(MAX_USERNAME_LENGTH, ErrorCode.USER_USERNAME_OVER_MAX)
    }

    private fun validateNickname(nickname: String) {
        nickname.isNotBlank().requireTrue(ErrorCode.USER_NICKNAME_BLANK)
        nickname.requireNotOver(MAX_NICKNAME_LENGTH, ErrorCode.USER_NICKNAME_OVER_MAX)
    }

    private fun validateAvatarEmoji(avatarEmoji: String) {
        avatarEmoji.requireNotOver(MAX_AVATAR_EMOJI_LENGTH, ErrorCode.USER_AVATAR_EMOJI_OVER_MAX)
        avatarEmoji.isEmojiLike().requireTrue(ErrorCode.USER_AVATAR_EMOJI_INVALID)
    }

    private fun String.isEmojiLike(): Boolean {
        var containsEmoji = false

        codePoints().forEach { codePoint ->
            if (codePoint.isEmojiBase()) {
                containsEmoji = true
                return@forEach
            }

            codePoint.isEmojiModifier().requireTrue(ErrorCode.USER_AVATAR_EMOJI_INVALID)
        }

        return containsEmoji
    }

    private fun Int.isEmojiBase(): Boolean =
        this in 0x2600..0x27BF ||
            this in 0x1F000..0x1FAFF ||
            Character.getType(this) == Character.OTHER_SYMBOL.toInt()

    private fun Int.isEmojiModifier(): Boolean =
        this == ZERO_WIDTH_JOINER ||
            this == VARIATION_SELECTOR_16 ||
            this in 0x1F3FB..0x1F3FF
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
