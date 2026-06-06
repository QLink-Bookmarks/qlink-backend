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
import com.qlink.link.dto.CreateLinkTodoRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.service.ScheduleTodoNotificationService
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class CreateLinkService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val folderRepository: FolderRepository,
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
) {
    suspend fun createLink(
        loginId: Long,
        request: CreateLinkRequest,
    ): CreateLinkResponse {
        val result =
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

                val createdTodos =
                    request.todos.map { todoRequest ->
                        todoRepository.insert(
                            todoRequest.toTodo(
                                linkId = createdLink.id!!,
                                ownerId = loginId,
                            ),
                        )
                    }

                CreateLinkResult(
                    response = CreateLinkResponse(createdLink.id!!),
                    createdTodos = createdTodos,
                )
            }

        result.createdTodos.forEach { scheduleTodoNotificationService.createForTodo(it) }

        return result.response
    }

    private data class CreateLinkResult(
        val response: CreateLinkResponse,
        val createdTodos: List<Todo>,
    )

    private fun CreateLinkTodoRequest.toTodo(
        linkId: Long,
        ownerId: Long,
    ): Todo =
        Todo.create(
            linkId = linkId,
            ownerId = ownerId,
            title = title,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            repeatTimezone = repeatTimezone,
            now = Clock.System.now(),
        )
}
