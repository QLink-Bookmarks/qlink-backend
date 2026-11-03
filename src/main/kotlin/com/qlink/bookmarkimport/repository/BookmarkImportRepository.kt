@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.repository

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailure
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.dto.ImportCursorValue
import com.qlink.bookmarkimport.dto.ImportItemCountsQuery
import com.qlink.bookmarkimport.dto.ImportThumbnailTargetQuery
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface BookmarkImportRepository {
    suspend fun lockOwner(ownerId: Long)

    suspend fun insert(bookmarkImport: BookmarkImport): BookmarkImport

    suspend fun update(bookmarkImport: BookmarkImport): BookmarkImport

    suspend fun findById(importId: Long): BookmarkImport?

    suspend fun findByIdForUpdate(importId: Long): BookmarkImport?

    suspend fun findByOwnerIdAndClientRequestId(
        ownerId: Long,
        clientRequestId: Uuid,
    ): BookmarkImport?

    suspend fun search(
        ownerId: Long,
        cursor: ImportCursorValue?,
        size: Int,
    ): List<BookmarkImport>

    suspend fun countItems(importIds: List<Long>): List<ImportItemCountsQuery>

    suspend fun insertFolders(folders: List<ImportFolder>): List<ImportFolder>

    suspend fun insertLinks(links: List<ImportLink>): List<ImportLink>

    suspend fun insertFailures(failures: List<ImportFailure>): List<ImportFailure>

    suspend fun findFoldersByImportId(importId: Long): List<ImportFolder>

    suspend fun findLinksByImportId(importId: Long): List<ImportLink>

    suspend fun findFailuresByImportId(importId: Long): List<ImportFailure>

    suspend fun findLinkById(importLinkId: Long): ImportLink?

    suspend fun reverseFolders(
        importFolderIds: List<Long>,
        reversedAt: Instant,
    )

    suspend fun reverseLinks(
        importLinkIds: List<Long>,
        reversedAt: Instant,
    )

    suspend fun updateThumbnailResult(link: ImportLink): Boolean

    suspend fun findThumbnailTargets(
        staleAfter: Duration,
        size: Int,
    ): List<ImportThumbnailTargetQuery>

    suspend fun countThumbnailTargets(staleAfter: Duration): Long

    suspend fun markThumbnailStarted(
        importLinkId: Long,
        staleAfter: Duration,
    ): Boolean
}
