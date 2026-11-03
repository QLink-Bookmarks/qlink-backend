package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.service.ImportTrackDomainService
import com.qlink.bookmarkimport.dto.GetImportReversalPreviewFolderResponse
import com.qlink.bookmarkimport.dto.GetImportReversalPreviewLinkResponse
import com.qlink.bookmarkimport.dto.GetImportReversalPreviewResponse
import com.qlink.bookmarkimport.dto.ImportReversalPreview
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository

class GetImportReversalPreviewService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val linkRepository: LinkRepository,
    private val bookmarkImportRepository: BookmarkImportRepository,
) {
    suspend fun getReversalPreview(
        loginId: Long,
        importId: Long,
    ): GetImportReversalPreviewResponse =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.IMPORT_OWNER_NOT_FOUND)

            val bookmarkImport = bookmarkImportRepository.findById(importId) ?: throw BusinessException(ErrorCode.IMPORT_NOT_FOUND)
            bookmarkImport.validateOwner(loginId)

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

            ImportTrackDomainService
                .reversalPreview(folders = folders, links = links, currentLinks = currentLinks, currentFolders = currentFolders)
                .toResponse()
        }

    private fun ImportReversalPreview.toResponse(): GetImportReversalPreviewResponse =
        GetImportReversalPreviewResponse(
            folders =
                folders.map { folder ->
                    GetImportReversalPreviewFolderResponse(
                        kind = folder.kind,
                        state = folder.state,
                        folderId = folder.folderId,
                        name = folder.name,
                        emoji = folder.emoji,
                        links =
                            folder.links.map {
                                GetImportReversalPreviewLinkResponse(linkId = it.linkId, title = it.title, url = it.url, state = it.state)
                            },
                    )
                },
        )
}
