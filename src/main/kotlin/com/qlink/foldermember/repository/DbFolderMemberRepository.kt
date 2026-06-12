package com.qlink.foldermember.repository

import com.qlink.folder.dto.FolderMemberQuery
import com.qlink.folder.dto.toFolderMemberQuery
import com.qlink.foldermember.domain.FolderMember
import com.qlink.foldermember.domain.MemberRole
import com.qlink.foldermember.repository.table.FolderMembers
import com.qlink.foldermember.repository.table.fromDomain
import com.qlink.foldermember.repository.table.toFolderMemberDomain
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.toJavaInstant

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

    override suspend fun findAllByFolderIdOrderByJoinedAtDesc(folderId: Long): List<FolderMemberQuery> =
        FolderMembers
            .join(
                otherTable = Users,
                joinType = JoinType.INNER,
                additionalConstraint = { FolderMembers.userId eq Users.id },
            ).select(
                FolderMembers.userId,
                FolderMembers.role,
                FolderMembers.joinedAt,
                Users.nickname,
                Users.avatarUrl,
                Users.avatarEmoji,
            ).where { FolderMembers.folderId eq folderId }
            .orderBy(FolderMembers.joinedAt to SortOrder.DESC, FolderMembers.userId to SortOrder.DESC)
            .map { it.toFolderMemberQuery() }

    override suspend fun findAllByFolderIdOrderByJoinedAtAsc(folderId: Long): List<FolderMember> =
        FolderMembers
            .selectAll()
            .where { FolderMembers.folderId eq folderId }
            .orderBy(FolderMembers.joinedAt to SortOrder.ASC, FolderMembers.userId to SortOrder.ASC)
            .map { it.toFolderMemberDomain() }

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

    override suspend fun updateRole(
        folderId: Long,
        userId: Long,
        role: MemberRole,
    ) {
        FolderMembers.update(
            where = {
                (FolderMembers.folderId eq folderId) and
                    (FolderMembers.userId eq userId)
            },
        ) {
            it[FolderMembers.role] = role.name
            it[FolderMembers.updatedAt] = Clock.System.now().toJavaInstant()
        }
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
