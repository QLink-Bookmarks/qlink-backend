package com.qlink.linkcontent.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

enum class ContentPriority(
    val value: Int,
) {
    INTERACTIVE(0),
    BULK(1),
    ;

    companion object {
        fun from(value: Int): ContentPriority =
            entries.firstOrNull { it.value == value }
                ?: throw BusinessException(ErrorCode.LINK_CONTENT_PRIORITY_NOT_SUPPORTED)
    }
}
