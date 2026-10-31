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
    val faviconUrl: String? = null,
    val isVideo: Boolean = false,
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
        faviconUrl: String? = this.faviconUrl,
        isVideo: Boolean = this.isVideo,
        previewPublicBaseUrl: String? = null,
        previewEnvironment: String? = null,
    ): Link {
        if (thumbnailUrl != this.thumbnailUrl && thumbnailUrl != null) {
            validatePreviewUrl(
                value = thumbnailUrl,
                publicBaseUrl = requireNotNull(previewPublicBaseUrl),
                environment = requireNotNull(previewEnvironment),
                errorCode = ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED,
            )
        }
        if (faviconUrl != this.faviconUrl && faviconUrl != null) {
            validatePreviewUrl(
                value = faviconUrl,
                publicBaseUrl = requireNotNull(previewPublicBaseUrl),
                environment = requireNotNull(previewEnvironment),
                errorCode = ErrorCode.LINK_FAVICON_URL_NOT_ALLOWED,
            )
        }

        return Link(
            id = id,
            ownerId = ownerId,
            folderId = folderId,
            url = url,
            title = title,
            summary = summary,
            memo = memo,
            tags = tags,
            thumbnailUrl = thumbnailUrl,
            faviconUrl = faviconUrl,
            isVideo = isVideo,
            sourceType = sourceType,
            status = status,
            workModelId = workModelId,
            favoriteAt = favoriteAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

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
            faviconUrl = faviconUrl,
            isVideo = isVideo,
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
                    faviconUrl = faviconUrl,
                    isVideo = isVideo,
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

    companion object {
        fun createFromRequest(
            ownerId: Long,
            folderId: Long?,
            url: String,
            title: String,
            summary: String?,
            memo: String?,
            tags: List<String>,
            thumbnailUrl: String?,
            faviconUrl: String?,
            isVideo: Boolean,
            sourceType: SourceType,
            status: LinkStatus,
            previewPublicBaseUrl: String,
            previewEnvironment: String,
        ): Link {
            thumbnailUrl?.let {
                validatePreviewUrl(it, previewPublicBaseUrl, previewEnvironment, ErrorCode.LINK_THUMBNAIL_URL_NOT_ALLOWED)
            }
            faviconUrl?.let {
                validatePreviewUrl(it, previewPublicBaseUrl, previewEnvironment, ErrorCode.LINK_FAVICON_URL_NOT_ALLOWED)
            }
            return Link(
                ownerId = ownerId,
                folderId = folderId,
                url = url,
                title = title,
                summary = summary,
                memo = memo,
                tags = tags,
                thumbnailUrl = thumbnailUrl,
                faviconUrl = faviconUrl,
                isVideo = isVideo,
                sourceType = sourceType,
                status = status,
            )
        }

        private fun validatePreviewUrl(
            value: String,
            publicBaseUrl: String,
            environment: String,
            errorCode: ErrorCode,
        ) {
            val candidate = runCatching { URI(value) }.getOrNull()
            val base = URI(publicBaseUrl)
            val filename = candidate?.rawPath?.removePrefix("/")
            val previewPrefix = "$environment-preview-"
            val faviconPrefix = "$environment-favicon-"
            val hasAllowedPrefix =
                (filename?.startsWith(previewPrefix) == true && filename.length > previewPrefix.length) ||
                    (filename?.startsWith(faviconPrefix) == true && filename.length > faviconPrefix.length)
            val valid =
                candidate != null &&
                    candidate.scheme.equals(base.scheme, ignoreCase = true) &&
                    candidate.host.equals(base.host, ignoreCase = true) &&
                    candidate.port == base.port &&
                    candidate.userInfo == null &&
                    candidate.rawQuery == null &&
                    candidate.rawFragment == null &&
                    candidate.rawPath?.startsWith("/") == true &&
                    filename != null &&
                    '/' !in filename &&
                    !filename.contains(Regex("(?i)%2f|%5c")) &&
                    hasAllowedPrefix
            if (!valid) throw BusinessException(errorCode)
        }
    }
}
