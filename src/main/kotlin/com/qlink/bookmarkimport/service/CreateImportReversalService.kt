package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.service.ImportTrackDomainService
import com.qlink.bookmarkimport.dto.CreateImportReversalRequest
import com.qlink.bookmarkimport.dto.ImportItemCountsQuery
import com.qlink.bookmarkimport.dto.ImportReversal
import com.qlink.bookmarkimport.dto.ImportSummary
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock
import kotlin.time.Instant

class CreateImportReversalService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val linkRepository: LinkRepository,
    private val bookmarkImportRepository: BookmarkImportRepository,
) {
    suspend fun createReversal(
        loginId: Long,
        importId: Long,
        request: CreateImportReversalRequest,
    ): ImportSummary =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.IMPORT_OWNER_NOT_FOUND)

            val bookmarkImport =
                bookmarkImportRepository.findByIdForUpdate(importId) ?: throw BusinessException(ErrorCode.IMPORT_NOT_FOUND)
            bookmarkImport.validateOwner(loginId)

            val now = Clock.System.now()
            val folders = bookmarkImportRepository.findFoldersByImportId(importId)
            val links = bookmarkImportRepository.findLinksByImportId(importId)
            val currentLinks =
                (
                    linkRepository.findAllByIds(links.map { it.linkId }) +
                        linkRepository.findAllByFolderIds(folders.filter { it.kind == ImportFolderKind.NEW }.map { it.folderId })
                ).distinctBy { it.id }
            val currentFolders =
                folderRepository.findAllByIds(
                    (folders.map { it.folderId } + currentLinks.mapNotNull { it.folderId }).distinct(),
                )
            val reversal =
                ImportTrackDomainService.reverse(
                    bookmarkImport = bookmarkImport,
                    linkIds = request.linkIds,
                    folderIds = request.folderIds,
                    folders = folders,
                    links = links,
                    currentLinks = currentLinks,
                    currentFolders = currentFolders,
                    now = now,
                )

            saveReversal(reversal = reversal, now = now)
                .toSummary(bookmarkImportRepository.countItems(listOf(importId)).single())
        }

    private suspend fun saveReversal(
        reversal: ImportReversal,
        now: Instant,
    ): BookmarkImport {
        if (reversal.linkIds.isEmpty() && reversal.folderIds.isEmpty()) {
            return reversal.bookmarkImport
        }

        linkRepository.detachFolders(reversal.folderIds)
        linkRepository.deleteAllByIds(reversal.linkIds)
        folderRepository.deleteAllByIds(reversal.folderIds)
        bookmarkImportRepository.reverseLinks(importLinkIds = reversal.importLinks.map { it.id!! }, reversedAt = now)
        bookmarkImportRepository.reverseFolders(importFolderIds = reversal.importFolders.map { it.id!! }, reversedAt = now)

        return bookmarkImportRepository.update(reversal.bookmarkImport)
    }

    private fun BookmarkImport.toSummary(counts: ImportItemCountsQuery): ImportSummary =
        ImportSummary.of(
            bookmarkImport = this,
            state =
                summaryState(
                    finishedThumbnailCount = counts.finishedThumbnailCount,
                    removableLinkCount = counts.removableLinkCount,
                    removableNewFolderCount = counts.removableNewFolderCount,
                ),
        )
}
