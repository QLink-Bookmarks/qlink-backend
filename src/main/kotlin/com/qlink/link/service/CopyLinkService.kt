package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.error.requireTrue
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.dto.CopyLinkRequest
import com.qlink.link.dto.CopyLinkResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository

class CopyLinkService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val linkRepository: LinkRepository,
) {
    suspend fun copyLink(
        loginId: Long,
        linkId: Long,
        request: CopyLinkRequest,
    ): CopyLinkResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

            val sharedFolder =
                folderRepository.findById(request.fromFolderId)
                    ?: throw BusinessException(ErrorCode.LINK_COPY_SHARED_FOLDER_NOT_FOUND)
            sharedFolder.sharedAt ?: throw BusinessException(ErrorCode.LINK_COPY_NOT_SHARED_FOLDER)
            folderMemberRepository
                .existsByFolderIdAndUserId(folderId = sharedFolder.id!!, userId = loginId)
                .requireTrue(ErrorCode.LINK_SHARED_FOLDER_ACCESS_DENIED)

            val personalFolder =
                folderRepository.findById(request.toFolderId)
                    ?: throw BusinessException(ErrorCode.LINK_FOLDER_NOT_FOUND)
            personalFolder.validateOwner(loginId)

            val link =
                linkRepository.findById(linkId)
                    ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)
            (link.folderId == request.fromFolderId).requireTrue(ErrorCode.LINK_COPY_FOLDER_MISMATCH)

            val copiedLink =
                linkRepository.insert(
                    link.addToFolder(
                        newOwnerId = loginId,
                        folderId = personalFolder.id!!,
                    ),
                )

            CopyLinkResponse(copiedLink.id!!)
        }
}
