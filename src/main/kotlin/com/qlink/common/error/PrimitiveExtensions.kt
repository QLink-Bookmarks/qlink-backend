package com.qlink.common.error

private const val ZERO_WIDTH_JOINER = 0x200D
private const val VARIATION_SELECTOR_16 = 0xFE0F

fun <T : Any> T?.orThrow(errorCode: ErrorCode): T = this ?: throw BusinessException(errorCode)

fun Boolean.requireTrue(errorCode: ErrorCode) {
    if (!this) {
        throw BusinessException(errorCode)
    }
}

fun Boolean.requireFalse(errorCode: ErrorCode) {
    if (this) {
        throw BusinessException(errorCode)
    }
}

fun String.requireNotBlank(errorCode: ErrorCode) {
    if (this.isBlank()) {
        throw BusinessException(errorCode)
    }
}

fun String.requireNotLessThan(
    length: Int,
    errorCode: ErrorCode,
) {
    if (this.length < length) {
        throw BusinessException(errorCode)
    }
}

fun String.requireNotOver(
    length: Int,
    errorCode: ErrorCode,
) {
    if (this.length > length) {
        throw BusinessException(errorCode)
    }
}

fun String.requiresEmoji(errorCode: ErrorCode) {
    var containsEmoji = false

    codePoints().forEach { codePoint ->
        if (codePoint.isEmojiBase()) {
            containsEmoji = true
            return@forEach
        }

        codePoint.isEmojiModifier().requireTrue(errorCode)
    }

    containsEmoji.requireTrue(errorCode)
}

private fun Int.isEmojiBase(): Boolean =
    this in 0x2600..0x27BF ||
        this in 0x1F000..0x1FAFF ||
        Character.getType(this) == Character.OTHER_SYMBOL.toInt()

private fun Int.isEmojiModifier(): Boolean =
    this == ZERO_WIDTH_JOINER ||
        this == VARIATION_SELECTOR_16 ||
        this in 0x1F3FB..0x1F3FF
