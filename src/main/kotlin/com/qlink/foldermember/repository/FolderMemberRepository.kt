package com.qlink.foldermember.repository

import com.qlink.folder.dto.FolderMemberQuery
import com.qlink.foldermember.domain.FolderMember
import com.qlink.foldermember.domain.MemberRole

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

    suspend fun findAllByFolderIdOrderByJoinedAtDesc(folderId: Long): List<FolderMemberQuery>

    suspend fun findAllByFolderIdOrderByJoinedAtAsc(folderId: Long): List<FolderMember>

    suspend fun insertIfAbsent(folderMember: FolderMember): FolderMember

    suspend fun updateRole(
        folderId: Long,
        userId: Long,
        role: MemberRole,
    )

    suspend fun deleteByFolderIdAndUserId(
        folderId: Long,
        userId: Long,
    )
}
