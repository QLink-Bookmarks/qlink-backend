package com.qlink.bookmarkimport.domain

import com.qlink.link.domain.Link
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ImportLink(
    val id: Long? = null,
    val importId: Long,
    val linkId: Long,
    val importFolderId: Long? = null,
    val url: String,
    val title: String,
    val thumbnailStatus: ThumbnailStatus = ThumbnailStatus.PENDING,
    val startedAt: Instant? = null,
    val reversedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    val isThumbnailPending: Boolean
        get() = thumbnailStatus == ThumbnailStatus.PENDING

    fun reversalState(current: Link?): ReversalLinkState =
        when {
            reversedAt != null -> ReversalLinkState.REVERSED
            current == null -> ReversalLinkState.DELETED
            current.isEditedFrom(this) -> ReversalLinkState.EDITED
            else -> ReversalLinkState.IMPORTED
        }

    fun isRemovable(current: Link?): Boolean = reversalState(current) in REMOVABLE_STATES

    fun reverse(now: Instant): ImportLink =
        copy(
            thumbnailStatus = if (isThumbnailPending) ThumbnailStatus.STOPPED else thumbnailStatus,
            reversedAt = now,
        )

    fun prepareThumbnail(current: Link?): ImportLink =
        when {
            current == null -> copy(thumbnailStatus = ThumbnailStatus.STOPPED)
            current.hasPreview -> completeThumbnail()
            else -> this
        }

    fun completeThumbnail(): ImportLink = copy(thumbnailStatus = ThumbnailStatus.DONE)

    fun failThumbnail(): ImportLink = copy(thumbnailStatus = ThumbnailStatus.FAILED)

    fun releaseThumbnail(): ImportLink = copy(thumbnailStatus = ThumbnailStatus.PENDING, startedAt = null)

    private fun Link.isEditedFrom(imported: ImportLink): Boolean =
        url != imported.url || title != imported.title || !memo.isNullOrEmpty() || tags.isNotEmpty()

    private fun copy(
        thumbnailStatus: ThumbnailStatus = this.thumbnailStatus,
        startedAt: Instant? = this.startedAt,
        reversedAt: Instant? = this.reversedAt,
    ): ImportLink =
        ImportLink(
            id = id,
            importId = importId,
            linkId = linkId,
            importFolderId = importFolderId,
            url = url,
            title = title,
            thumbnailStatus = thumbnailStatus,
            startedAt = startedAt,
            reversedAt = reversedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        val THUMBNAIL_STALE_AFTER: Duration = 1.hours

        private val REMOVABLE_STATES = setOf(ReversalLinkState.IMPORTED, ReversalLinkState.EDITED)

        fun create(
            importId: Long,
            linkId: Long,
            importFolderId: Long?,
            url: String,
            title: String,
        ): ImportLink =
            ImportLink(
                importId = importId,
                linkId = linkId,
                importFolderId = importFolderId,
                url = url,
                title = title,
            )
    }
}
