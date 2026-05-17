package com.qlink.link.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.UserRepository

class CreateLinkService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
) {
    suspend fun createLink(
        loginId: Long,
        request: CreateLinkRequest,
    ): CreateLinkResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)
            request.folderId?.let {
                folderRepository.emptyById(it).requireFalse(ErrorCode.LINK_FOLDER_NOT_FOUND)
            }

            val link =
                Link(
                    ownerId = loginId,
                    folderId = request.folderId,
                    url = request.url,
                    title = request.title,
                    summary = request.summary,
                    tags = request.tags,
                    thumbnailUrl = request.thumbnailUrl,
                    sourceType = request.sourceType,
                    reminderAt = request.reminderAt,
                )

            CreateLinkResponse(linkRepository.insert(link).id!!)
        }
}
