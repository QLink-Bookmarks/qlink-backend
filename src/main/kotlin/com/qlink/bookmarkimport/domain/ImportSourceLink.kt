package com.qlink.bookmarkimport.domain

import kotlin.time.Instant

class ImportSourceLink(
    val key: String,
    val url: String,
    val title: String? = null,
    val folderKey: String? = null,
    val createdAt: Instant? = null,
) {
    fun failureReason(): ImportFailureReason? =
        when {
            url.length > URL_MAX_LENGTH -> ImportFailureReason.URL_INVALID
            title.isNullOrBlank() || title.length > TITLE_MAX_LENGTH -> ImportFailureReason.TITLE_INVALID
            else -> null
        }

    fun createdAtOr(now: Instant): Instant = createdAt?.coerceAtMost(now) ?: now

    companion object {
        const val URL_MAX_LENGTH = 2048
        const val TITLE_MAX_LENGTH = 300
    }
}
