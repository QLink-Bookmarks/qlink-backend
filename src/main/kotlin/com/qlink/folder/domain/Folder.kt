package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotBlank
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requiresEmoji
import kotlin.random.Random
import kotlin.time.Instant

private const val MAX_FOLDER_NAME_LENGTH = 100
private const val MAX_FOLDER_EMOJI_LENGTH = 20
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

    fun isOwnedBy(userId: Long): Boolean = ownerId == userId

    fun validateOwner(ownerId: Long) {
        if (!isOwnedBy(ownerId)) {
            throw BusinessException(ErrorCode.FOLDER_DIFFERENT_OWNER)
        }
    }

    fun update(
        name: String,
        emoji: String?,
        sharedAt: Instant? = this.sharedAt,
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

    fun delegateTo(newOwnerId: Long): Folder =
        Folder(
            id = id,
            ownerId = newOwnerId,
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
        name.requireNotBlank(ErrorCode.FOLDER_NAME_BLANK)
        name.requireNotOver(MAX_FOLDER_NAME_LENGTH, ErrorCode.FOLDER_NAME_OVER_MAX)
    }

    private fun validateEmoji(emoji: String) {
        emoji.requireNotOver(MAX_FOLDER_EMOJI_LENGTH, ErrorCode.FOLDER_EMOJI_OVER_MAX)
        emoji.requiresEmoji(ErrorCode.FOLDER_EMOJI_INVALID)
    }
}
