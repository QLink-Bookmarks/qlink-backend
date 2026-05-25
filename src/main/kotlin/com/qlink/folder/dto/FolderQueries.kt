@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import kotlinx.serialization.Serializable
import java.sql.ResultSet
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

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

@Serializable
enum class FolderSearchOrder {
    LATEST,
    EARLIEST,
    LAXICO,
    SIMILAR,
    ;

    companion object {
        fun from(value: String): FolderSearchOrder? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class FolderSearchCursor(
    val order: FolderSearchOrder,
    val value: FolderSearchCursorValue,
)

@Serializable
data class FolderSearchCursorValue(
    val id: Long? = null,
    val name: String? = null,
    val score: Double? = null,
)

enum class FolderDeleteOption {
    CASCADE,
    NULL,
    ;

    companion object {
        fun from(value: String?): FolderDeleteOption =
            when (value?.lowercase() ?: "null") {
                "cascade" -> CASCADE
                "null" -> NULL
                else -> throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }
    }
}

fun ResultSet.toSearchFoldersQuery(): SearchFoldersQuery =
    SearchFoldersQuery(
        id = getLong("id"),
        name = getString("name"),
        emoji = getString("emoji"),
        sharedAt = getTimestamp("shared_at")?.toInstant()?.toKotlinInstant(),
        createdAt = getTimestamp("created_at").toInstant().toKotlinInstant(),
        shareCounts = getInt("share_counts"),
        linkCounts = getInt("link_counts"),
        score = getDouble("score"),
    )
