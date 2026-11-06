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
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.linkcontent.worker.LinkContentSignal
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class CopyLinkService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val linkRepository: LinkRepository,
    private val linkContentRepository: LinkContentRepository,
    private val linkContentSignal: LinkContentSignal,
) {
    suspend fun copyLink(
        loginId: Long,
        linkId: Long,
        request: CopyLinkRequest,
    ): CopyLinkResponse =
        tx
            .required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

                folderRepository
                    .findById(request.fromFolderId)
                    ?.also { it.sharedAt ?: throw BusinessException(ErrorCode.LINK_COPY_NOT_SHARED_FOLDER) }
                    ?.also {
                        folderMemberRepository
                            .existsByFolderIdAndUserId(folderId = it.id!!, userId = loginId)
                            .requireTrue(ErrorCode.LINK_SHARED_FOLDER_ACCESS_DENIED)
                    }
                    ?: throw BusinessException(ErrorCode.LINK_SHARE_FOLDER_NOT_FOUND)

                val personalFolderId =
                    request.toFolderId?.let { toFolderId ->
                        folderRepository
                            .findById(toFolderId)
                            ?.also { it.validateOwner(loginId) }
                            ?.id
                            ?: throw BusinessException(ErrorCode.LINK_TARGET_FOLDER_NOT_FOUND)
                    }

                val link =
                    linkRepository
                        .findById(linkId)
                        ?.also { found ->
                            found.folderId
                                ?.let { (it == request.fromFolderId).requireTrue(ErrorCode.LINK_COPY_FOLDER_MISMATCH) }
                                ?: throw BusinessException(ErrorCode.LINK_COPY_LINK_FOLDER_NOT_FOUND)
                        }
                        ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)

                val copiedLink = linkRepository.insert(link.addToFolder(newOwnerId = loginId, folderId = personalFolderId))
                linkContentRepository.insertAll(
                    listOf(
                        LinkContent.create(
                            linkId = copiedLink.id!!,
                            sourceUrl = copiedLink.url,
                            priority = ContentPriority.INTERACTIVE,
                            now = Clock.System.now(),
                        ),
                    ),
                )

                CopyLinkResponse(copiedLink.id)
            }.also { linkContentSignal.wake() }
}
