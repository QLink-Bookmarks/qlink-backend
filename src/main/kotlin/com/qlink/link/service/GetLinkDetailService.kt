package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.dto.GetLinkDetailResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.todo.dto.LinkDetailTodoQuery
import com.qlink.todo.repository.TodoRepository

class GetLinkDetailService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val folderRepository: FolderRepository,
    private val todoRepository: TodoRepository,
) {
    suspend fun getLinkDetail(
        loginId: Long,
        linkId: Long,
    ): GetLinkDetailResponse =
        tx.readOnly {
            val link = linkRepository.findById(linkId) ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)

            link.validateOwner(loginId)

            val folder = link.folderId?.let { folderRepository.findById(it) }
            val todos = todoRepository.findAllByLinkIdForLinkDetail(link.id!!)

            link.toResponse(
                folderName = folder?.name,
                folderEmoji = folder?.emoji,
                todos = todos,
            )
        }

    private fun Link.toResponse(
        folderName: String?,
        folderEmoji: String?,
        todos: List<LinkDetailTodoQuery>,
    ): GetLinkDetailResponse =
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
            folderEmoji = folderEmoji,
            todos = todos,
        )
}
