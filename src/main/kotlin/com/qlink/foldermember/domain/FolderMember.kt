package com.qlink.foldermember.domain

import kotlin.time.Instant

data class FolderMember(
    val folderId: Long,
    val userId: Long,
    val userName: String,
    val role: String,
    val joinedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun owner(
            folderId: Long,
            userId: Long,
            userName: String,
            joinedAt: Instant,
        ): FolderMember =
            FolderMember(
                folderId = folderId,
                userId = userId,
                userName = userName,
                role = "OWNER",
                joinedAt = joinedAt,
                createdAt = joinedAt,
                updatedAt = joinedAt,
            )

        fun member(
            folderId: Long,
            userId: Long,
            userName: String,
            joinedAt: Instant,
        ): FolderMember =
            FolderMember(
                folderId = folderId,
                userId = userId,
                userName = userName,
                role = "MEMBER",
                joinedAt = joinedAt,
                createdAt = joinedAt,
                updatedAt = joinedAt,
            )
    }
}
