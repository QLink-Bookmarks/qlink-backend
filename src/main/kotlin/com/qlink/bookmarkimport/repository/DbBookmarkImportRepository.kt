@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.repository

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailure
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.dto.ImportCursorValue
import com.qlink.bookmarkimport.dto.ImportItemCountsQuery
import com.qlink.bookmarkimport.dto.ImportThumbnailTargetQuery
import com.qlink.bookmarkimport.repository.table.BookmarkImportFailures
import com.qlink.bookmarkimport.repository.table.BookmarkImportFolders
import com.qlink.bookmarkimport.repository.table.BookmarkImportLinks
import com.qlink.bookmarkimport.repository.table.BookmarkImports
import com.qlink.bookmarkimport.repository.table.fromDomain
import com.qlink.bookmarkimport.repository.table.refreshBookmarkImportUpdatedAt
import com.qlink.bookmarkimport.repository.table.refreshImportLinkUpdatedAt
import com.qlink.bookmarkimport.repository.table.toBookmarkImportDomain
import com.qlink.bookmarkimport.repository.table.toImportFailureDomain
import com.qlink.bookmarkimport.repository.table.toImportFolderDomain
import com.qlink.bookmarkimport.repository.table.toImportLinkDomain
import com.qlink.common.number.foldToInt
import com.qlink.common.search.DbNowMinus
import com.qlink.folder.repository.table.Folders
import com.qlink.link.repository.table.Links
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val READ_COMMITTED = "read committed"
private const val IMPORT_OWNER_LOCK_CLASS = 156_001
private val FINISHED_THUMBNAIL_STATUSES = listOf(ThumbnailStatus.DONE, ThumbnailStatus.FAILED, ThumbnailStatus.STOPPED)

class DbBookmarkImportRepository : BookmarkImportRepository {
    override suspend fun lockOwner(ownerId: Long) {
        TransactionManager.current().run {
            val isolation = exec("SHOW transaction_isolation") { it.takeIf { row -> row.next() }?.getString(1) }
            if (isolation != READ_COMMITTED) {
                exec("SET TRANSACTION ISOLATION LEVEL READ COMMITTED")
            }
            exec(
                "SELECT pg_advisory_xact_lock(?, ?)",
                listOf(IntegerColumnType() to IMPORT_OWNER_LOCK_CLASS, IntegerColumnType() to ownerId.foldToInt()),
            )
        }
    }

    override suspend fun insert(bookmarkImport: BookmarkImport): BookmarkImport =
        BookmarkImports
            .insertReturning { it.fromDomain(bookmarkImport) }
            .single()
            .toBookmarkImportDomain()

    override suspend fun update(bookmarkImport: BookmarkImport): BookmarkImport =
        BookmarkImports
            .updateReturning(where = { BookmarkImports.id eq bookmarkImport.id!! }) {
                it.fromDomain(bookmarkImport)
                it.refreshBookmarkImportUpdatedAt()
            }.single()
            .toBookmarkImportDomain()

    override suspend fun findById(importId: Long): BookmarkImport? =
        BookmarkImports
            .selectAll()
            .where { BookmarkImports.id eq importId }
            .singleOrNull()
            ?.toBookmarkImportDomain()

    override suspend fun findByIdForUpdate(importId: Long): BookmarkImport? =
        BookmarkImports
            .selectAll()
            .where { BookmarkImports.id eq importId }
            .forUpdate()
            .singleOrNull()
            ?.toBookmarkImportDomain()

    override suspend fun findByOwnerIdAndClientRequestId(
        ownerId: Long,
        clientRequestId: Uuid,
    ): BookmarkImport? =
        BookmarkImports
            .selectAll()
            .where { (BookmarkImports.ownerId eq ownerId) and (BookmarkImports.clientRequestId eq clientRequestId) }
            .singleOrNull()
            ?.toBookmarkImportDomain()

    override suspend fun search(
        ownerId: Long,
        cursor: ImportCursorValue?,
        size: Int,
    ): List<BookmarkImport> =
        BookmarkImports
            .selectAll()
            .where { BookmarkImports.ownerId eq ownerId }
            .apply {
                cursor?.let { value ->
                    val createdAt = value.createdAt!!.toJavaInstant()

                    andWhere {
                        (BookmarkImports.createdAt less createdAt) or
                            ((BookmarkImports.createdAt eq createdAt) and (BookmarkImports.id less value.id!!))
                    }
                }
            }.orderBy(BookmarkImports.createdAt to SortOrder.DESC, BookmarkImports.id to SortOrder.DESC)
            .limit(size + 1)
            .map { it.toBookmarkImportDomain() }

    override suspend fun countItems(importIds: List<Long>): List<ImportItemCountsQuery> {
        if (importIds.isEmpty()) {
            return emptyList()
        }

        val linkCount = BookmarkImportLinks.id.count()
        val folderCount = BookmarkImportFolders.id.count()
        val finishedThumbnailCounts =
            BookmarkImportLinks
                .select(BookmarkImportLinks.importId, linkCount)
                .where {
                    (BookmarkImportLinks.importId inList importIds) and
                        (BookmarkImportLinks.thumbnailStatus inList FINISHED_THUMBNAIL_STATUSES)
                }.countBy(BookmarkImportLinks.importId, linkCount)
        val removableLinkCounts =
            BookmarkImportLinks
                .join(
                    otherTable = Links,
                    joinType = JoinType.INNER,
                    additionalConstraint = { BookmarkImportLinks.linkId eq Links.id },
                ).select(BookmarkImportLinks.importId, linkCount)
                .where { (BookmarkImportLinks.importId inList importIds) and BookmarkImportLinks.reversedAt.isNull() }
                .countBy(BookmarkImportLinks.importId, linkCount)
        val removableNewFolderCounts =
            BookmarkImportFolders
                .join(
                    otherTable = Folders,
                    joinType = JoinType.INNER,
                    additionalConstraint = { BookmarkImportFolders.folderId eq Folders.id },
                ).select(BookmarkImportFolders.importId, folderCount)
                .where {
                    (BookmarkImportFolders.importId inList importIds) and
                        (BookmarkImportFolders.kind eq ImportFolderKind.NEW) and
                        BookmarkImportFolders.reversedAt.isNull() and
                        Folders.sharedAt.isNull()
                }.countBy(BookmarkImportFolders.importId, folderCount)

        return importIds.map { importId ->
            ImportItemCountsQuery(
                importId = importId,
                finishedThumbnailCount = finishedThumbnailCounts[importId] ?: 0,
                removableLinkCount = removableLinkCounts[importId] ?: 0,
                removableNewFolderCount = removableNewFolderCounts[importId] ?: 0,
            )
        }
    }

    override suspend fun insertFolders(folders: List<ImportFolder>): List<ImportFolder> =
        BookmarkImportFolders
            .batchInsert(folders) { fromDomain(it) }
            .map { it.toImportFolderDomain() }

    override suspend fun insertLinks(links: List<ImportLink>): List<ImportLink> =
        BookmarkImportLinks
            .batchInsert(links) { fromDomain(it) }
            .map { it.toImportLinkDomain() }

    override suspend fun insertFailures(failures: List<ImportFailure>): List<ImportFailure> =
        BookmarkImportFailures
            .batchInsert(failures) { fromDomain(it) }
            .map { it.toImportFailureDomain() }

    override suspend fun findFoldersByImportId(importId: Long): List<ImportFolder> =
        BookmarkImportFolders
            .selectAll()
            .where { BookmarkImportFolders.importId eq importId }
            .orderBy(BookmarkImportFolders.id to SortOrder.ASC)
            .map { it.toImportFolderDomain() }

    override suspend fun findLinksByImportId(importId: Long): List<ImportLink> =
        BookmarkImportLinks
            .selectAll()
            .where { BookmarkImportLinks.importId eq importId }
            .orderBy(BookmarkImportLinks.id to SortOrder.ASC)
            .map { it.toImportLinkDomain() }

    override suspend fun findFailuresByImportId(importId: Long): List<ImportFailure> =
        BookmarkImportFailures
            .selectAll()
            .where { BookmarkImportFailures.importId eq importId }
            .orderBy(BookmarkImportFailures.id to SortOrder.ASC)
            .map { it.toImportFailureDomain() }

    override suspend fun findLinkById(importLinkId: Long): ImportLink? =
        BookmarkImportLinks
            .selectAll()
            .where { BookmarkImportLinks.id eq importLinkId }
            .singleOrNull()
            ?.toImportLinkDomain()

    override suspend fun reverseFolders(
        importFolderIds: List<Long>,
        reversedAt: Instant,
    ) {
        BookmarkImportFolders.update(
            where = { (BookmarkImportFolders.id inList importFolderIds) and BookmarkImportFolders.reversedAt.isNull() },
        ) {
            it[BookmarkImportFolders.reversedAt] = reversedAt.toJavaInstant()
        }
    }

    override suspend fun reverseLinks(
        importLinkIds: List<Long>,
        reversedAt: Instant,
    ) {
        BookmarkImportLinks.update(
            where = {
                (BookmarkImportLinks.id inList importLinkIds) and
                    BookmarkImportLinks.reversedAt.isNull() and
                    (BookmarkImportLinks.thumbnailStatus eq ThumbnailStatus.PENDING)
            },
        ) {
            it[BookmarkImportLinks.thumbnailStatus] = ThumbnailStatus.STOPPED
        }
        BookmarkImportLinks.update(
            where = { (BookmarkImportLinks.id inList importLinkIds) and BookmarkImportLinks.reversedAt.isNull() },
        ) {
            it[BookmarkImportLinks.reversedAt] = reversedAt.toJavaInstant()
            it.refreshImportLinkUpdatedAt()
        }
    }

    override suspend fun updateThumbnailResult(link: ImportLink): Boolean =
        BookmarkImportLinks.update(
            where = {
                (BookmarkImportLinks.id eq link.id!!) and
                    (BookmarkImportLinks.thumbnailStatus eq ThumbnailStatus.PENDING) and
                    BookmarkImportLinks.reversedAt.isNull()
            },
        ) {
            it[BookmarkImportLinks.thumbnailStatus] = link.thumbnailStatus
            it[BookmarkImportLinks.startedAt] = link.startedAt?.toJavaInstant()
            it.refreshImportLinkUpdatedAt()
        } == 1

    override suspend fun findThumbnailTargets(
        staleAfter: Duration,
        size: Int,
    ): List<ImportThumbnailTargetQuery> =
        BookmarkImportLinks
            .join(
                otherTable = Links,
                joinType = JoinType.LEFT,
                additionalConstraint = { BookmarkImportLinks.linkId eq Links.id },
            ).select(BookmarkImportLinks.id, BookmarkImportLinks.url, Links.url)
            .where { isThumbnailTarget(staleAfter) }
            .orderBy(Links.createdAt to SortOrder.DESC, BookmarkImportLinks.id to SortOrder.DESC)
            .limit(size)
            .map {
                ImportThumbnailTargetQuery(
                    importLinkId = it[BookmarkImportLinks.id],
                    url = it.getOrNull(Links.url) ?: it[BookmarkImportLinks.url],
                )
            }

    override suspend fun countThumbnailTargets(staleAfter: Duration): Long =
        BookmarkImportLinks
            .selectAll()
            .where { isThumbnailTarget(staleAfter) }
            .count()

    override suspend fun markThumbnailStarted(
        importLinkId: Long,
        staleAfter: Duration,
    ): Boolean =
        BookmarkImportLinks.update(
            where = { (BookmarkImportLinks.id eq importLinkId) and isThumbnailTarget(staleAfter) },
        ) {
            it[BookmarkImportLinks.startedAt] = CurrentTimestamp
            it[BookmarkImportLinks.updatedAt] = CurrentTimestamp
        } == 1

    private fun isThumbnailTarget(staleAfter: Duration): Op<Boolean> =
        (BookmarkImportLinks.thumbnailStatus eq ThumbnailStatus.PENDING) and
            (BookmarkImportLinks.startedAt.isNull() or (BookmarkImportLinks.startedAt less DbNowMinus(staleAfter)))

    private fun Query.countBy(
        key: Column<Long>,
        count: ExpressionWithColumnType<Long>,
    ): Map<Long, Int> = groupBy(key).associate { it[key] to it[count].toInt() }
}
