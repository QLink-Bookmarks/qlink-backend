@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderResponse(
    val id: Long,
)

@Serializable
data class GetFoldersContentResponse(
    val id: Long,
    val ownerId: Long,
    val ownerNickname: String?,
    val name: String,
    val emoji: String?,
    val isShared: Boolean,
    val shareCounts: Int,
    val linkCounts: Int,
)

@Serializable
data class GetFolderMembersResponse(
    val ownerId: Long,
    val ownerNickname: String,
    val ownerAvatarUrl: String?,
    val ownerAvatarEmoji: String?,
    val members: List<GetFolderMemberResponse>,
)

@Serializable
data class GetFolderMemberResponse(
    val userId: Long,
    val role: String,
    val userNickname: String,
    val avatarUrl: String?,
    val avatarEmoji: String?,
)

@Serializable
data class UpdateFolderResponse(
    val id: Long,
)
