@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

import com.qlink.common.search.SearchCursor
import com.qlink.common.search.SearchOrder
import com.qlink.foldermember.repository.table.FolderMembers
import com.qlink.user.repository.table.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

const val DEFAULT_FOLDER_SEARCH_ORDER = "latest"

data class SearchFoldersQuery(
    val id: Long,
    val ownerId: Long,
    val ownerNickname: String?,
    val name: String,
    val emoji: String?,
    val sharedAt: Instant?,
    val createdAt: Instant,
    val shareCounts: Int,
    val linkCounts: Int,
    val score: Double,
)

data class FolderMemberQuery(
    val userId: Long,
    val role: String,
    val userNickname: String,
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val joinedAt: Instant,
)

typealias FolderSearchOrder = SearchOrder

typealias FolderSearchCursor = SearchCursor<FolderSearchCursorValue>

@Serializable
data class FolderSearchCursorValue(
    val id: Long? = null,
    val name: String? = null,
    val score: Double? = null,
)

fun ResultRow.toFolderMemberQuery(): FolderMemberQuery =
    FolderMemberQuery(
        userId = this[FolderMembers.userId],
        role = this[FolderMembers.role],
        userNickname = this[Users.nickname],
        avatarUrl = this[Users.avatarUrl],
        avatarEmoji = this[Users.avatarEmoji],
        joinedAt = this[FolderMembers.joinedAt].toKotlinInstant(),
    )
