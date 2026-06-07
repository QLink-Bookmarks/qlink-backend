package com.qlink.foldermember.repository

import com.qlink.foldermember.domain.FolderMember

interface FolderMemberRepository {
    suspend fun findByFolderIdAndUserId(
        folderId: Long,
        userId: Long,
    ): FolderMember?

    suspend fun existsByFolderIdAndUserId(
        folderId: Long,
        userId: Long,
    ): Boolean

    suspend fun existsByFolderId(folderId: Long): Boolean

    suspend fun insertIfAbsent(folderMember: FolderMember): FolderMember

    suspend fun deleteByFolderIdAndUserId(
        folderId: Long,
        userId: Long,
    )
}
