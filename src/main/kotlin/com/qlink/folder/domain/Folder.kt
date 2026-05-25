package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import kotlin.random.Random
import kotlin.time.Instant

private const val MAX_FOLDER_NAME_LENGTH = 100
private const val MAX_FOLDER_EMOJI_LENGTH = 20
private const val ZERO_WIDTH_JOINER = 0x200D
private const val VARIATION_SELECTOR_16 = 0xFE0F
private val DEFAULT_FOLDER_EMOJIS = listOf("📁", "🗂️", "🔥", "✨", "📚", "🧠", "🚀", "🌿")

class Folder(
    val id: Long? = null,
    val ownerId: Long,
    val name: String,
    val emoji: String? = null,
    val sharedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        validateName(name)
        emoji?.let(::validateEmoji)
    }

    fun validateOwner(ownerId: Long) {
        if (this.ownerId != ownerId) {
            throw BusinessException(ErrorCode.FOLDER_DIFFERENT_OWNER)
        }
    }

    fun update(
        name: String,
        emoji: String?,
    ): Folder =
        Folder(
            id = id,
            ownerId = ownerId,
            name = name,
            emoji = emoji,
            sharedAt = sharedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun create(
            ownerId: Long,
            name: String,
            emoji: String?,
            sharedAt: Instant?,
        ): Folder =
            Folder(
                ownerId = ownerId,
                name = name,
                emoji = emoji ?: DEFAULT_FOLDER_EMOJIS[Random.nextInt(DEFAULT_FOLDER_EMOJIS.size)],
                sharedAt = sharedAt,
            )
    }

    private fun validateName(name: String) {
        name.isNotBlank().requireTrue(ErrorCode.FOLDER_NAME_BLANK)
        name.requireNotOver(MAX_FOLDER_NAME_LENGTH, ErrorCode.FOLDER_NAME_OVER_MAX)
    }

    private fun validateEmoji(emoji: String) {
        emoji.requireNotOver(MAX_FOLDER_EMOJI_LENGTH, ErrorCode.FOLDER_EMOJI_OVER_MAX)
        emoji.isEmojiLike().requireTrue(ErrorCode.FOLDER_EMOJI_INVALID)
    }

    private fun String.isEmojiLike(): Boolean {
        var containsEmoji = false

        codePoints().forEach { codePoint ->
            if (codePoint.isEmojiBase()) {
                containsEmoji = true
                return@forEach
            }

            codePoint.isEmojiModifier().requireTrue(ErrorCode.FOLDER_EMOJI_INVALID)
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
