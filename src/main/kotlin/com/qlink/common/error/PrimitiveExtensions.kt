package com.qlink.common.error

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

fun String.requireNotOver(
    length: Int,
    errorCode: ErrorCode,
) {
    if (this.length > length) {
        throw BusinessException(errorCode)
    }
}
