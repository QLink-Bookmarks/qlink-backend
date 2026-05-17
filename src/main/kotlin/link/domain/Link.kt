package com.qlink.link.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import java.net.URI
import kotlin.time.Instant

private const val MAX_TITLE_LENGTH = 300
private val WEB_PROTOCOL = setOf("http", "https")

class Link(
    val id: Long? = null,
    val ownerId: Long,
    val folderId: Long? = null,
    val url: String,
    val title: String,
    val summary: String? = null,
    val memo: String? = null,
    val tags: List<String>,
    val thumbnailUrl: String? = null,
    val sourceType: SourceType,
    val reminderAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        validateUrl(url)
        validateTitle(title)

        // TODO: summary, memo, tag도 검증 필요할지?

        thumbnailUrl?.let { validateUrl(it) }
    }

    private fun validateUrl(url: String) {
        url.isNotBlank().requireTrue(ErrorCode.LINK_URL_BLANK)

        val uri = runCatching { URI(url.trim()) }.getOrElse { e ->
            throw BusinessException(
                ErrorCode.LINK_URL_WRONG_FORMAT,
                e
            )
        }

        uri.host.isNullOrBlank().requireFalse(ErrorCode.LINK_URL_WRONG_HOST)
        uri.isWebScheme().requireTrue(ErrorCode.LINK_URL_NOT_HTTP)
    }

    private fun validateTitle(title: String) {
        title.isNotBlank().requireTrue(ErrorCode.LINK_TITLE_BLANK)
        title.requireNotOver(MAX_TITLE_LENGTH, ErrorCode.LINK_TITLE_OVER_MAX)
    }

    private fun URI.isWebScheme(): Boolean {
        return scheme.lowercase() in WEB_PROTOCOL
    }
}
