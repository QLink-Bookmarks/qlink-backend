package com.qlink.foldermember.domain

import kotlin.time.Instant

data class FolderMember(
    val folderId: Long,
    val userId: Long,
    val userName: String,
    val role: MemberRole,
    val joinedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun canWriteLink(): Boolean = role.canWriteLink()

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
                role = MemberRole.OWNER,
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
                role = MemberRole.MEMBER,
                joinedAt = joinedAt,
                createdAt = joinedAt,
                updatedAt = joinedAt,
            )
    }
}
