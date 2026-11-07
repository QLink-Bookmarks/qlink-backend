package com.qlink.link.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.service.FolderAccessValidator
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.dto.CreateLinkResponse
import com.qlink.link.dto.CreateLinkTodoRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.link.worker.LinkPreviewFillSignal
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.linkcontent.worker.LinkContentSignal
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
    private val folderAccessValidator: FolderAccessValidator,
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
    private val urlResolver: PreviewImageUrlResolver,
    private val linkContentRepository: LinkContentRepository,
    private val linkContentSignal: LinkContentSignal,
    private val linkPreviewFillSignal: LinkPreviewFillSignal,
) {
    suspend fun createLink(
        loginId: Long,
        request: CreateLinkRequest,
    ): CreateLinkResponse {
        val result =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)
                request.folderId?.let { folderAccessValidator.validateWritable(it, loginId) }

                val link =
                    Link.createFromRequest(
                        ownerId = loginId,
                        folderId = request.folderId,
                        url = request.url,
                        title = request.title,
                        summary = request.summary,
                        memo = request.memo,
                        tags = request.tags,
                        thumbnailUrl = request.thumbnailUrl,
                        faviconUrl = request.faviconUrl,
                        isVideo = request.isVideo,
                        sourceType = request.sourceType,
                        status = LinkStatus.C,
                        previewPublicBaseUrl = urlResolver.publicBaseUrl,
                        previewEnvironment = urlResolver.environment,
                    )

                val createdLink = linkRepository.insert(link)
                linkContentRepository.insertAll(
                    listOf(
                        LinkContent.create(
                            linkId = createdLink.id!!,
                            sourceUrl = createdLink.url,
                            priority = ContentPriority.INTERACTIVE,
                            now = Clock.System.now(),
                        ),
                    ),
                )

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
                    previewFillLinkId = createdLink.id.takeUnless { createdLink.hasPreview },
                )
            }

        linkContentSignal.wake()
        result.previewFillLinkId?.let(linkPreviewFillSignal::wake)
        result.createdTodos.forEach { scheduleTodoNotificationService.createForTodo(it) }

        return result.response
    }

    private data class CreateLinkResult(
        val response: CreateLinkResponse,
        val createdTodos: List<Todo>,
        val previewFillLinkId: Long?,
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
