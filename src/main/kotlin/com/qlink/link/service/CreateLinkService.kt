package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository

class CreateLinkService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val todoRepository: TodoRepository,
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
                folderRepository.findById(it)?.also { it.validateOwner(loginId) }
                    ?: throw BusinessException(ErrorCode.LINK_FOLDER_NOT_FOUND)
            }

            val link =
                Link(
                    ownerId = loginId,
                    folderId = request.folderId,
                    url = request.url,
                    title = request.title,
                    summary = request.summary,
                    memo = request.memo,
                    tags = request.tags,
                    thumbnailUrl = request.thumbnailUrl,
                    sourceType = request.sourceType,
                    status = LinkStatus.C,
                )

            val createdLink = linkRepository.insert(link)

            request.todos.forEach { todoRequest ->
                todoRepository.insert(
                    Todo(
                        linkId = createdLink.id!!,
                        ownerId = loginId,
                        title = todoRequest.title,
                        reminderAt = todoRequest.reminderAt,
                    ),
                )
            }

            CreateLinkResponse(createdLink.id!!)
        }
}
