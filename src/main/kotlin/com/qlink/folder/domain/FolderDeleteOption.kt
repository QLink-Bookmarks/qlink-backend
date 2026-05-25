package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode

enum class FolderDeleteOption {
    CASCADE,
    NULL,
    ;

    companion object {
        fun from(value: String?): FolderDeleteOption =
            when (value?.lowercase() ?: "null") {
                "cascade" -> CASCADE
                "null" -> NULL
                else -> throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }
    }
}
