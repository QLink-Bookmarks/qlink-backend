package com.qlink.bookmarkimport.domain

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

    fun reversalState(
        isDeleted: Boolean,
        isEdited: Boolean,
    ): ReversalLinkState =
        when {
            reversedAt != null -> ReversalLinkState.REVERSED
            isDeleted -> ReversalLinkState.DELETED
            isEdited -> ReversalLinkState.EDITED
            else -> ReversalLinkState.IMPORTED
        }

    fun withState(
        isDeleted: Boolean,
        isEdited: Boolean,
    ): ImportLinkState = ImportLinkState(link = this, isDeleted = isDeleted, isEdited = isEdited)

    fun isRemovable(state: ReversalLinkState): Boolean = state in REMOVABLE_STATES

    fun reverse(now: Instant): ImportLink =
        copy(
            thumbnailStatus = if (isThumbnailPending) ThumbnailStatus.STOPPED else thumbnailStatus,
            reversedAt = now,
        )

    fun prepareThumbnail(
        isDeleted: Boolean,
        hasPreview: Boolean,
    ): ImportLink =
        when {
            isDeleted -> copy(thumbnailStatus = ThumbnailStatus.STOPPED)
            hasPreview -> completeThumbnail()
            else -> this
        }

    fun completeThumbnail(): ImportLink = copy(thumbnailStatus = ThumbnailStatus.DONE)

    fun failThumbnail(): ImportLink = copy(thumbnailStatus = ThumbnailStatus.FAILED)

    fun releaseThumbnail(): ImportLink = copy(thumbnailStatus = ThumbnailStatus.PENDING, startedAt = null)

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
