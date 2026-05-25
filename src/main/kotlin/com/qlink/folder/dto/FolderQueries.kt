@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

import com.qlink.common.search.SearchCursor
import com.qlink.common.search.SearchOrder
import kotlinx.serialization.Serializable
import kotlin.time.Instant

const val DEFAULT_FOLDER_SEARCH_ORDER = "latest"

data class SearchFoldersQuery(
    val id: Long,
    val name: String,
    val emoji: String?,
    val sharedAt: Instant?,
    val createdAt: Instant,
    val shareCounts: Int,
    val linkCounts: Int,
    val score: Double,
)

typealias FolderSearchOrder = SearchOrder

typealias FolderSearchCursor = SearchCursor<FolderSearchCursorValue>

@Serializable
data class FolderSearchCursorValue(
    val id: Long? = null,
    val name: String? = null,
    val score: Double? = null,
)
