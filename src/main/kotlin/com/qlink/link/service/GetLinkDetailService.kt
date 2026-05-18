package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.dto.GetLinkDetailResponse
import com.qlink.link.repository.LinkRepository

class GetLinkDetailService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val folderRepository: FolderRepository,
) {
    suspend fun getLinkDetail(
        loginId: Long,
        linkId: Long,
    ): GetLinkDetailResponse =
        tx.readOnly {
            val link = linkRepository.findById(linkId) ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)

            link.validateOwner(loginId)

            val folderName = link.folderId?.let { folderRepository.findById(it)?.name }

            link.toResponse(folderName)
        }

    private fun Link.toResponse(folderName: String?): GetLinkDetailResponse =
        GetLinkDetailResponse(
            id = id!!,
            url = url,
            title = title,
            summary = summary,
            tags = tags,
            memo = memo,
            sourceType = sourceType,
            createdAt = createdAt!!,
            folderId = folderId,
            folderName = folderName,
        )
}
