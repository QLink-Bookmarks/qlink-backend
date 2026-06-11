package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.dto.UpdateLinkRequest
import com.qlink.link.dto.UpdateLinkResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository

class UpdateLinkService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
) {
    suspend fun updateLink(
        loginId: Long,
        linkId: Long,
        request: UpdateLinkRequest,
    ): UpdateLinkResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

            val link = linkRepository.findById(linkId) ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)
            link.validateOwner(loginId)

            request.folderId?.let {
                folderRepository.findById(it)?.also { folder -> folder.validateOwner(loginId) }
                    ?: throw BusinessException(ErrorCode.LINK_FOLDER_NOT_FOUND)
            }

            val updatedLink =
                link.update(
                    folderId = request.folderId,
                    url = request.url,
                    title = request.title,
                    summary = request.summary,
                    memo = request.memo,
                    tags = request.tags,
                    thumbnailUrl = request.thumbnailUrl,
                    sourceType = request.sourceType,
                    isFavorite = request.isFavorite,
                )

            val savedLink = linkRepository.update(updatedLink)

            savedLink.toResponse()
        }

    private fun Link.toResponse(): UpdateLinkResponse =
        UpdateLinkResponse(
            folderId = folderId,
            url = url,
            title = title,
            summary = summary,
            memo = memo,
            tags = tags,
            thumbnailUrl = thumbnailUrl,
            sourceType = sourceType,
            isFavorite = favoriteAt != null,
        )
}
