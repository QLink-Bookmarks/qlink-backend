@file:Suppress("ktlint:standard:filename")

package com.qlink.bookmarkimport.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant

data class ImportItemCountsQuery(
    val importId: Long,
    val finishedThumbnailCount: Int,
    val removableLinkCount: Int,
    val removableNewFolderCount: Int,
)

@Serializable
data class ImportCursorValue(
    val createdAt: Instant? = null,
    val id: Long? = null,
)

data class ImportThumbnailTargetQuery(
    val importLinkId: Long,
    val url: String,
)
