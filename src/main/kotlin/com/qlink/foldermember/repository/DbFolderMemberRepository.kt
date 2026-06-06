package com.qlink.foldermember.repository

import com.qlink.foldermember.domain.FolderMember
import com.qlink.foldermember.repository.table.FolderMembers
import com.qlink.foldermember.repository.table.fromDomain
import com.qlink.foldermember.repository.table.toFolderMemberDomain
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class DbFolderMemberRepository : FolderMemberRepository {
    override suspend fun findByFolderIdAndUserId(
        folderId: Long,
        userId: Long,
    ): FolderMember? =
        FolderMembers
            .selectAll()
            .where {
                (FolderMembers.folderId eq folderId) and
                    (FolderMembers.userId eq userId)
            }.singleOrNull()
            ?.toFolderMemberDomain()

    override suspend fun existsByFolderIdAndUserId(
        folderId: Long,
        userId: Long,
    ): Boolean =
        FolderMembers
            .select(FolderMembers.folderId)
            .where {
                (FolderMembers.folderId eq folderId) and
                    (FolderMembers.userId eq userId)
            }.empty()
            .not()

    override suspend fun existsByFolderId(folderId: Long): Boolean =
        FolderMembers
            .select(FolderMembers.folderId)
            .where { FolderMembers.folderId eq folderId }
            .limit(1)
            .empty()
            .not()

    override suspend fun insertIfAbsent(folderMember: FolderMember): FolderMember {
        findByFolderIdAndUserId(
            folderId = folderMember.folderId,
            userId = folderMember.userId,
        )?.let { return it }

        return FolderMembers
            .insertReturning { it.fromDomain(folderMember) }
            .single()
            .toFolderMemberDomain()
    }

    override suspend fun deleteByFolderIdAndUserId(
        folderId: Long,
        userId: Long,
    ) {
        FolderMembers.deleteWhere {
            (FolderMembers.folderId eq folderId) and
                (FolderMembers.userId eq userId)
        }
    }
}
