package com.qlink.link.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import java.net.URI
import kotlin.time.Clock
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
    tags: List<String>,
    val thumbnailUrl: String? = null,
    val sourceType: SourceType,
    val status: LinkStatus = LinkStatus.C,
    val workModelId: Long? = null,
    val favoriteAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    val tags: List<String> = tags.distinct()

    init {
        validateUrl(url)
        validateTitle(title)

        // TODO: summary, memo, tag도 검증 필요할지?

        thumbnailUrl?.let { validateUrl(it) }
    }

    fun validateOwner(ownerId: Long) {
        if (this.ownerId != ownerId) {
            throw BusinessException(ErrorCode.LINK_DIFFERENT_OWNER)
        }
    }

    fun update(
        folderId: Long?,
        url: String,
        title: String,
        summary: String?,
        memo: String?,
        tags: List<String>,
        thumbnailUrl: String?,
        sourceType: SourceType,
        favoriteAt: Instant? = this.favoriteAt,
    ): Link =
        Link(
            id = id,
            ownerId = ownerId,
            folderId = folderId,
            url = url,
            title = title,
            summary = summary,
            memo = memo,
            tags = tags,
            thumbnailUrl = thumbnailUrl,
            sourceType = sourceType,
            status = status,
            workModelId = workModelId,
            favoriteAt = favoriteAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun addToFolder(
        newOwnerId: Long,
        folderId: Long?,
    ): Link =
        Link(
            ownerId = newOwnerId,
            folderId = folderId,
            url = url,
            title = title,
            summary = summary,
            memo = null,
            tags = tags,
            thumbnailUrl = thumbnailUrl,
            sourceType = SourceType.COPY,
            status = LinkStatus.C,
        )

    fun changeFavorite(
        isFavorite: Boolean?,
        now: Instant = Clock.System.now(),
    ): Link? =
        when (isFavorite) {
            null -> {
                null
            }

            else -> {
                Link(
                    id = id,
                    ownerId = ownerId,
                    folderId = folderId,
                    url = url,
                    title = title,
                    summary = summary,
                    memo = memo,
                    tags = tags,
                    thumbnailUrl = thumbnailUrl,
                    sourceType = sourceType,
                    status = status,
                    workModelId = workModelId,
                    favoriteAt = resolveFavoriteAt(isFavorite, now),
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
            }
        }

    private fun resolveFavoriteAt(
        isFavorite: Boolean,
        now: Instant,
    ): Instant? =
        when {
            !isFavorite -> null
            else -> favoriteAt ?: now
        }

    private fun validateUrl(url: String) {
        url.isNotBlank().requireTrue(ErrorCode.LINK_URL_BLANK)

        val uri =
            runCatching { URI(url.trim()) }.getOrElse { e ->
                throw BusinessException(
                    ErrorCode.LINK_URL_WRONG_FORMAT,
                    e,
                )
            }

        uri.host.isNullOrBlank().requireFalse(ErrorCode.LINK_URL_WRONG_HOST)
        uri.isWebScheme().requireTrue(ErrorCode.LINK_URL_NOT_HTTP)
    }

    private fun validateTitle(title: String) {
        title.isNotBlank().requireTrue(ErrorCode.LINK_TITLE_BLANK)
        title.requireNotOver(MAX_TITLE_LENGTH, ErrorCode.LINK_TITLE_OVER_MAX)
    }

    private fun URI.isWebScheme(): Boolean = scheme.lowercase() in WEB_PROTOCOL
}
