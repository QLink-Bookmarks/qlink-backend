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
    val name: String,
    val emoji: String?,
    val isShared: Boolean,
    val shareCounts: Int,
    val linkCounts: Int,
)

@Serializable
data class UpdateFolderResponse(
    val id: Long,
)
