package com.qlink.linkcontent.domain

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import com.qlink.common.text.takeKeepingSurrogatePairs
import kotlin.time.Instant

class CollectedPage(
    text: String,
    title: String? = null,
    siteName: String? = null,
    author: String? = null,
    val publishedAt: Instant? = null,
    val canonicalUrl: String? = null,
    val finalUrl: String? = null,
    language: String? = null,
    isTruncated: Boolean = false,
) {
    val text: String = text.takeKeepingSurrogatePairs(MAX_TEXT_LENGTH)
    val isTruncated: Boolean = isTruncated || text.length > MAX_TEXT_LENGTH
    val title: String? = title?.takeKeepingSurrogatePairs(MAX_TITLE_LENGTH)
    val siteName: String? = siteName?.takeKeepingSurrogatePairs(MAX_SITE_NAME_LENGTH)
    val author: String? = author?.takeKeepingSurrogatePairs(MAX_AUTHOR_LENGTH)
    val language: String? = language?.takeKeepingSurrogatePairs(MAX_LANGUAGE_LENGTH)

    init {
        this.text.isNotBlank().requireTrue(ErrorCode.LINK_CONTENT_TEXT_BLANK)
    }

    companion object {
        const val MAX_TEXT_LENGTH = 100_000
        const val MAX_TITLE_LENGTH = 500
        const val MAX_SITE_NAME_LENGTH = 200
        const val MAX_AUTHOR_LENGTH = 200
        const val MAX_LANGUAGE_LENGTH = 16
    }
}
