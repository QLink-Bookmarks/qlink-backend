package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportSource
import com.qlink.bookmarkimport.domain.ImportSourceFolder
import com.qlink.bookmarkimport.domain.ImportSourceLink
import com.qlink.bookmarkimport.domain.service.ImportPlanDomainService
import com.qlink.bookmarkimport.dto.CreateImportPreviewFolderResponse
import com.qlink.bookmarkimport.dto.CreateImportPreviewLinkResponse
import com.qlink.bookmarkimport.dto.CreateImportPreviewRequest
import com.qlink.bookmarkimport.dto.CreateImportPreviewResponse
import com.qlink.bookmarkimport.dto.ImportPreview
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository

class CreateImportPreviewService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val linkRepository: LinkRepository,
) {
    suspend fun createPreview(
        loginId: Long,
        request: CreateImportPreviewRequest,
    ): CreateImportPreviewResponse =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.IMPORT_PREVIEW_OWNER_NOT_FOUND)

            ImportPlanDomainService
                .preview(
                    source = request.toSource(),
                    myFolders = folderRepository.findAllByOwnerId(loginId),
                    myUrls = linkRepository.findAllUrlsByOwnerId(loginId),
                ).toResponse()
        }

    private fun CreateImportPreviewRequest.toSource(): ImportSource =
        ImportSource(
            folders = folders.map { ImportSourceFolder(key = it.key, name = it.name) },
            links = links.map { ImportSourceLink(key = it.key, url = it.url) },
        )

    private fun ImportPreview.toResponse(): CreateImportPreviewResponse =
        CreateImportPreviewResponse(
            folders =
                folders.map {
                    CreateImportPreviewFolderResponse(
                        key = it.key,
                        matchType = it.matchType,
                        folderId = it.folderId,
                        suggestedName = it.suggestedName,
                    )
                },
            links = links.map { CreateImportPreviewLinkResponse(key = it.key, status = it.status) },
        )
}
