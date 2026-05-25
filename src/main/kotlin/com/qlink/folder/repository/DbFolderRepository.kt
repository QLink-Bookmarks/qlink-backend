package com.qlink.folder.repository

import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.FolderSearchCursor
import com.qlink.folder.dto.FolderSearchCursorValue
import com.qlink.folder.dto.FolderSearchOrder
import com.qlink.folder.dto.SearchFoldersQuery
import com.qlink.folder.dto.toSearchFoldersQuery
import com.qlink.folder.repository.table.Folders
import com.qlink.folder.repository.table.fromDomain
import com.qlink.folder.repository.table.refreshUpdatedAt
import com.qlink.folder.repository.table.toFolderDomain
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.updateReturning

class DbFolderRepository : FolderRepository {
    override suspend fun findById(id: Long): Folder? =
        Folders
            .selectAll()
            .where { Folders.id eq id }
            .singleOrNull()
            ?.toFolderDomain()

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
        val cursorValue = cursor?.value
        val baseFilters = mutableListOf("f.owner_id = ?")
        val scoreArgs = mutableListOf<Pair<IColumnType<*>, Any?>>()
        val baseArgs = mutableListOf<Pair<IColumnType<*>, Any?>>(LongColumnType() to ownerId)
        val cursorArgs = mutableListOf<Pair<IColumnType<*>, Any?>>()

        if (normalizedQuery != null) {
            scoreArgs += TextColumnType() to normalizedQuery
            baseFilters += "f.name ILIKE '%' || ? || '%'"
            baseArgs += TextColumnType() to normalizedQuery
        }

        val sql =
            """
            WITH searched_folders AS (
                SELECT
                    f.id,
                    f.name,
                    f.emoji,
                    f.shared_at,
                    f.created_at,
                    COALESCE(member_counts.member_count, 0) + 1 AS share_counts,
                    COALESCE(link_counts.link_count, 0) AS link_counts,
                    ${scoreSql(normalizedQuery != null)} AS score
                FROM folders f
                LEFT JOIN (
                    SELECT
                        fm.folder_id,
                        COUNT(*) AS member_count
                    FROM folder_members fm
                    GROUP BY fm.folder_id
                ) member_counts ON member_counts.folder_id = f.id
                LEFT JOIN (
                    SELECT
                        l.folder_id,
                        COUNT(*) AS link_count
                    FROM links l
                    WHERE l.folder_id IS NOT NULL
                    GROUP BY l.folder_id
                ) link_counts ON link_counts.folder_id = f.id
                WHERE ${baseFilters.joinToString(" AND ")}
            )
            SELECT *
            FROM searched_folders
            WHERE ${cursorFilterSql(order, cursorValue, cursorArgs)}
            ORDER BY ${orderBySql(order)}
            LIMIT ?
            """.trimIndent()

        val args =
            buildList {
                addAll(scoreArgs)
                addAll(baseArgs)
                addAll(cursorArgs)
                add(IntegerColumnType() to (size + 1))
            }

        return TransactionManager
            .current()
            .exec(sql, args, explicitStatementType = StatementType.SELECT) { resultSet ->
                generateSequence { if (resultSet.next()) resultSet.toSearchFoldersQuery() else null }.toList()
            }.orEmpty()
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

    private fun scoreSql(keywordProvided: Boolean): String =
        if (keywordProvided) {
            "public.bigm_similarity(CAST(coalesce(f.name, '') AS text), CAST(? AS text))"
        } else {
            "0.0"
        }

    private fun cursorFilterSql(
        order: FolderSearchOrder,
        cursorValue: FolderSearchCursorValue?,
        args: MutableList<Pair<IColumnType<*>, Any?>>,
    ): String =
        when (order) {
            FolderSearchOrder.LATEST -> {
                cursorValue?.id?.let {
                    args += LongColumnType() to it
                    "id < ?"
                } ?: "1 = 1"
            }

            FolderSearchOrder.EARLIEST -> {
                cursorValue?.id?.let {
                    args += LongColumnType() to it
                    "id > ?"
                } ?: "1 = 1"
            }

            FolderSearchOrder.LAXICO -> {
                cursorValue?.name?.let {
                    args += TextColumnType() to it
                    args += TextColumnType() to it
                    args += LongColumnType() to (cursorValue.id ?: Long.MIN_VALUE)
                    "(name > ? OR (name = ? AND id > ?))"
                } ?: "1 = 1"
            }

            FolderSearchOrder.SIMILAR -> {
                cursorValue?.score?.let {
                    args += DoubleColumnType() to it
                    args += DoubleColumnType() to it
                    args += LongColumnType() to (cursorValue.id ?: Long.MAX_VALUE)
                    "(score < ? OR (score = ? AND id < ?))"
                } ?: "1 = 1"
            }
        }

    private fun orderBySql(order: FolderSearchOrder): String =
        when (order) {
            FolderSearchOrder.LATEST -> "id DESC"
            FolderSearchOrder.EARLIEST -> "id ASC"
            FolderSearchOrder.LAXICO -> "name ASC, id ASC"
            FolderSearchOrder.SIMILAR -> "score DESC, id DESC"
        }
}
