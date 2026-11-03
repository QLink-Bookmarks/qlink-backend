package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailure
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.service.ImportTrackDomainService
import com.qlink.bookmarkimport.dto.GetImportDetailFailureResponse
import com.qlink.bookmarkimport.dto.GetImportDetailFolderResponse
import com.qlink.bookmarkimport.dto.GetImportDetailLinkResponse
import com.qlink.bookmarkimport.dto.GetImportDetailResponse
import com.qlink.bookmarkimport.dto.ImportItemCountsQuery
import com.qlink.bookmarkimport.dto.ImportSummary
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.user.repository.UserRepository

class GetImportDetailService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val bookmarkImportRepository: BookmarkImportRepository,
) {
    suspend fun getImportDetail(
        loginId: Long,
        importId: Long,
    ): GetImportDetailResponse =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.IMPORT_OWNER_NOT_FOUND)

            val bookmarkImport = bookmarkImportRepository.findById(importId) ?: throw BusinessException(ErrorCode.IMPORT_NOT_FOUND)
            bookmarkImport.validateOwner(loginId)

            val folders = bookmarkImportRepository.findFoldersByImportId(importId)
            val currentFolderById = folderRepository.findAllByIds(folders.map { it.folderId }).associateBy { it.id }
            val linksByImportFolderId = bookmarkImportRepository.findLinksByImportId(importId).groupBy { it.importFolderId }
            val counts = bookmarkImportRepository.countItems(listOf(importId)).single()

            GetImportDetailResponse.of(
                summary = bookmarkImport.toSummary(counts),
                folders =
                    folders.map { it.toResponse(currentFolderById[it.folderId], linksByImportFolderId[it.id].orEmpty()) } +
                        listOfNotNull(linksByImportFolderId[null]?.let(::unfiledResponse)),
                failures = bookmarkImportRepository.findFailuresByImportId(importId).map { it.toResponse() },
            )
        }

    private fun ImportFolder.toResponse(
        current: Folder?,
        links: List<ImportLink>,
    ): GetImportDetailFolderResponse =
        GetImportDetailFolderResponse(
            kind = kind,
            folderId = folderId,
            name = ImportTrackDomainService.folderName(folder = this, current = current),
            emoji = current?.emoji,
            links = links.map { it.toResponse() },
        )

    private fun unfiledResponse(links: List<ImportLink>): GetImportDetailFolderResponse =
        GetImportDetailFolderResponse(
            kind = ImportFolderKind.UNFILED,
            folderId = null,
            name = null,
            emoji = null,
            links = links.map { it.toResponse() },
        )

    private fun ImportLink.toResponse(): GetImportDetailLinkResponse =
        GetImportDetailLinkResponse(linkId = linkId, title = title, url = url)

    private fun ImportFailure.toResponse(): GetImportDetailFailureResponse =
        GetImportDetailFailureResponse(title = title, url = url, reason = reason)

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
