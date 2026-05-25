@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant

const val DEFAULT_LINK_SEARCH_ORDER = "latest"

data class SearchLinksQuery(
    val id: Long,
    val folderId: Long?,
    val folderName: String?,
    val folderEmoji: String?,
    val url: String,
    val title: String,
    val tags: List<String>,
    val createdAt: Instant,
    val score: Double,
    val titleScore: Double,
    val urlScore: Double,
    val tagsScore: Double,
    val summaryScore: Double,
    val memoScore: Double,
)

@Serializable
enum class LinkSearchOrder {
    LATEST,
    EARLIEST,
    LAXICO,
    SIMILAR,
    ;

    companion object {
        fun from(value: String): LinkSearchOrder? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class LinkSearchCursor(
    val order: LinkSearchOrder,
    val value: LinkSearchCursorValue,
)

@Serializable
data class LinkSearchCursorValue(
    val id: Long? = null,
    val title: String? = null,
    val score: Double? = null,
    val titleScore: Double? = null,
    val urlScore: Double? = null,
    val tagsScore: Double? = null,
    val summaryScore: Double? = null,
    val memoScore: Double? = null,
)
