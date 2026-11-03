package com.qlink.bookmarkimport.domain

import com.qlink.common.error.ErrorCode

enum class ImportFailureReason {
    URL_INVALID,
    TITLE_INVALID,
    UNKNOWN,
    ;

    companion object {
        private val URL_ERRORS =
            setOf(
                ErrorCode.LINK_URL_BLANK,
                ErrorCode.LINK_URL_WRONG_FORMAT,
                ErrorCode.LINK_URL_WRONG_HOST,
                ErrorCode.LINK_URL_NOT_HTTP,
            )
        private val TITLE_ERRORS =
            setOf(
                ErrorCode.LINK_TITLE_BLANK,
                ErrorCode.LINK_TITLE_OVER_MAX,
            )

        fun from(errorCode: ErrorCode): ImportFailureReason =
            when (errorCode) {
                in URL_ERRORS -> URL_INVALID
                in TITLE_ERRORS -> TITLE_INVALID
                else -> UNKNOWN
            }
    }
}
