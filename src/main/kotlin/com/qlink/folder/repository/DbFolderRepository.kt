package com.qlink.folder.repository

import com.qlink.common.search.bigmSimilarity
import com.qlink.common.search.doubleLiteral
import com.qlink.common.search.longLiteral
import com.qlink.common.search.lowerText
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.FolderSearchCursor
import com.qlink.folder.dto.FolderSearchCursorValue
import com.qlink.folder.dto.FolderSearchOrder
import com.qlink.folder.dto.SearchFoldersQuery
import com.qlink.folder.repository.table.Folders
import com.qlink.folder.repository.table.fromDomain
import com.qlink.folder.repository.table.refreshUpdatedAt
import com.qlink.folder.repository.table.toFolderDomain
import com.qlink.foldermember.repository.table.FolderMembers
import com.qlink.link.repository.table.Links
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.toKotlinInstant

class DbFolderRepository : FolderRepository {
    override suspend fun findById(id: Long): Folder? =
        Folders
            .selectAll()
            .where { Folders.id eq id }
            .singleOrNull()
            ?.toFolderDomain()

    override suspend fun findAllByOwnerId(ownerId: Long): List<Folder> =
        Folders
            .selectAll()
            .where { Folders.ownerId eq ownerId }
            .orderBy(Folders.id to SortOrder.ASC)
            .map { it.toFolderDomain() }

    override suspend fun existsByOwnerIdAndName(
        ownerId: Long,
        name: String,
    ): Boolean =
        Folders
            .select(Folders.id)
            .where { (Folders.ownerId eq ownerId) and (Folders.name eq name) }
            .empty()
            .not()

    override suspend fun existsByOwnerIdAndNameAndIdNot(
        ownerId: Long,
        name: String,
        folderId: Long,
    ): Boolean =
        Folders
            .select(Folders.id)
            .where {
                (Folders.ownerId eq ownerId) and
                    (Folders.name eq name) and
                    (Folders.id neq folderId)
            }.empty()
            .not()

    override suspend fun insert(folder: Folder): Folder = Folders.insertReturning { it.fromDomain(folder) }.single().toFolderDomain()

    override suspend fun search(
        ownerId: Long,
        query: String?,
        order: FolderSearchOrder,
        cursor: FolderSearchCursor?,
        size: Int,
    ): List<SearchFoldersQuery> {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val loweredQuery = normalizedQuery?.lowercase()
        val score = (normalizedQuery?.let { bigmSimilarity(Folders.name, it) } ?: doubleLiteral(0.0)).alias("score")
        val ownerUsers = Users.alias("owner_users")
        val ownerNickname = ownerUsers[Users.nickname].alias("owner_nickname")
        val joined =
            Folders
                .join(
                    otherTable = FolderMembers,
                    joinType = JoinType.LEFT,
                    additionalConstraint = {
                        (Folders.id eq FolderMembers.folderId) and
                            (FolderMembers.userId eq ownerId)
                    },
                ).join(
                    otherTable = ownerUsers,
                    joinType = JoinType.INNER,
                    additionalConstraint = { Folders.ownerId eq ownerUsers[Users.id] },
                )

        val folders =
            joined
                .select(
                    Folders.id,
                    Folders.ownerId,
                    ownerNickname,
                    Folders.name,
                    Folders.emoji,
                    Folders.sharedAt,
                    Folders.createdAt,
                    score,
                ).where {
                    (Folders.ownerId eq ownerId) or
                        (
                            (FolderMembers.userId eq ownerId) and
                                Folders.sharedAt.isNotNull()
                        )
                }.apply {
                    loweredQuery?.let { keyword ->
                        andWhere { lowerText(Folders.name) like "%$keyword%" }
                    }
                    cursor?.value?.let { cursorValue ->
                        andWhere { cursorFilter(order, cursorValue, score) }
                    }
                }.orderBy(*orderBy(order, score))
                .limit(size + 1)
                .map { it.toSearchFoldersQuery(score, ownerNickname, ownerId) }

        if (folders.isEmpty()) {
            return emptyList()
        }

        val folderIds = folders.map { it.id }
        val memberCounts =
            FolderMembers
                .select(FolderMembers.folderId)
                .where { FolderMembers.folderId inList folderIds }
                .groupingBy { it[FolderMembers.folderId] }
                .eachCount()
        val linkCounts =
            Links
                .select(Links.folderId)
                .where { Links.folderId inList folderIds }
                .groupingBy { it[Links.folderId]!! }
                .eachCount()

        return folders.map {
            it.copy(
                shareCounts = (memberCounts[it.id] ?: 0) + 1,
                linkCounts = linkCounts[it.id] ?: 0,
            )
        }
    }

    override suspend fun update(folder: Folder): Folder =
        Folders
            .updateReturning(where = { Folders.id eq folder.id!! }) {
                it.fromDomain(folder)
                it.refreshUpdatedAt()
            }.single()
            .toFolderDomain()

    override suspend fun deleteById(folderId: Long) {
        Folders.deleteWhere { id eq folderId }
    }

    private fun cursorFilter(
        order: FolderSearchOrder,
        cursorValue: FolderSearchCursorValue,
        score: org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias<Double>,
    ): Op<Boolean> =
        when (order) {
            FolderSearchOrder.LATEST -> {
                Folders.id less longLiteral(cursorValue.id ?: Long.MIN_VALUE)
            }

            FolderSearchOrder.EARLIEST -> {
                Folders.id greater longLiteral(cursorValue.id ?: Long.MAX_VALUE)
            }

            FolderSearchOrder.LAXICO -> {
                (Folders.name greater (cursorValue.name ?: "")) or
                    (
                        (Folders.name eq (cursorValue.name ?: "")) and
                            (Folders.id greater longLiteral(cursorValue.id ?: Long.MIN_VALUE))
                    )
            }

            FolderSearchOrder.SIMILAR -> {
                (score less doubleLiteral(cursorValue.score ?: Double.MIN_VALUE)) or
                    (
                        (score eq doubleLiteral(cursorValue.score ?: Double.MIN_VALUE)) and
                            (Folders.id less longLiteral(cursorValue.id ?: Long.MAX_VALUE))
                    )
            }
        }

    private fun orderBy(
        order: FolderSearchOrder,
        score: org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias<Double>,
    ): Array<Pair<org.jetbrains.exposed.v1.core.Expression<*>, SortOrder>> =
        when (order) {
            FolderSearchOrder.LATEST -> arrayOf(Folders.id to SortOrder.DESC)
            FolderSearchOrder.EARLIEST -> arrayOf(Folders.id to SortOrder.ASC)
            FolderSearchOrder.LAXICO -> arrayOf(Folders.name to SortOrder.ASC, Folders.id to SortOrder.ASC)
            FolderSearchOrder.SIMILAR -> arrayOf(score to SortOrder.DESC, Folders.id to SortOrder.DESC)
        }

    private fun ResultRow.toSearchFoldersQuery(
        score: org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias<Double>,
        ownerNickname: org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias<String>,
        loginId: Long,
    ): SearchFoldersQuery =
        SearchFoldersQuery(
            id = this[Folders.id],
            ownerId = this[Folders.ownerId],
            ownerNickname = this[ownerNickname].takeIf { this[Folders.ownerId] != loginId },
            name = this[Folders.name],
            emoji = this[Folders.emoji],
            sharedAt = this[Folders.sharedAt]?.toKotlinInstant(),
            createdAt = this[Folders.createdAt].toKotlinInstant(),
            shareCounts = 0,
            linkCounts = 0,
            score = this[score],
        )
}
