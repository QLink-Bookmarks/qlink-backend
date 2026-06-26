package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.service.FolderAccessValidator
import com.qlink.link.domain.Link
import com.qlink.link.dto.PatchLinkRequest
import com.qlink.link.dto.PatchLinkResponse
import com.qlink.link.dto.PatchLinkTodoRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.service.ScheduleTodoNotificationService
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class PatchLinkService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val folderAccessValidator: FolderAccessValidator,
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
) {
    suspend fun patchLink(
        loginId: Long,
        linkId: Long,
        request: PatchLinkRequest,
    ): PatchLinkResponse {
        val result =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

                val link =
                    linkRepository.findById(linkId)?.also { it.validateOwner(loginId) }
                        ?: throw BusinessException(ErrorCode.LINK_NOT_FOUND)

                val targetFolderId = request.resolveFolderId(link, loginId)
                val targetMemo = request.resolveMemo(link)
                val targetTags = request.resolveTags(link)
                val todoChangeSet = request.resolveTodoChangeSet(linkId, loginId)

                val updatedLink =
                    link.update(
                        folderId = targetFolderId,
                        url = link.url,
                        title = link.title,
                        summary = link.summary,
                        memo = targetMemo,
                        tags = targetTags,
                        thumbnailUrl = link.thumbnailUrl,
                        sourceType = link.sourceType,
                    )

                val savedLink =
                    if (updatedLink.hasSamePatchFieldsAs(link)) {
                        link
                    } else {
                        linkRepository.update(updatedLink)
                    }

                val deletedTodoIds =
                    todoChangeSet
                        ?.todoIdsToDelete
                        ?.onEach { todoRepository.deleteById(it) }
                        .orEmpty()
                val updatedTodos = todoChangeSet?.todosToUpdate?.map { todoRepository.update(it) }.orEmpty()
                val createdTodos = todoChangeSet?.todosToCreate?.map { todoRepository.insert(it) }.orEmpty()

                PatchLinkResult(
                    response =
                        PatchLinkResponse(
                            folderId = savedLink.folderId,
                            memo = savedLink.memo,
                            tags = savedLink.tags,
                            todos = todoRepository.findAllByLinkIdForLinkDetail(linkId),
                        ),
                    deletedTodoIds = deletedTodoIds,
                    updatedTodos = updatedTodos,
                    createdTodos = createdTodos,
                )
            }

        result.deletedTodoIds.forEach { scheduleTodoNotificationService.cancelForTodo(it) }
        result.updatedTodos.forEach { scheduleTodoNotificationService.replaceForTodo(it) }
        result.createdTodos.forEach { scheduleTodoNotificationService.createForTodo(it) }

        return result.response
    }

    private suspend fun PatchLinkRequest.resolveFolderId(
        link: Link,
        loginId: Long,
    ): Long? {
        val folderId = folderId ?: return link.folderId

        if (folderId == 0L) {
            return null
        }

        folderAccessValidator.validateWritable(folderId, loginId)

        return folderId
    }

    private fun PatchLinkRequest.resolveMemo(link: Link): String? =
        when (memo) {
            null -> link.memo
            "" -> null
            else -> memo
        }

    private fun PatchLinkRequest.resolveTags(link: Link): List<String> = tags ?: link.tags

    private suspend fun PatchLinkRequest.resolveTodoChangeSet(
        linkId: Long,
        loginId: Long,
    ): TodoChangeSet? = todos?.let { buildTodoChangeSet(linkId, loginId, it) }

    private suspend fun buildTodoChangeSet(
        linkId: Long,
        loginId: Long,
        todos: List<PatchLinkTodoRequest>,
    ): TodoChangeSet {
        val existingTodos = todoRepository.findAllByLinkId(linkId)
        val existingTodosById = existingTodos.associateBy { it.id!! }
        val requestedIds = todos.mapNotNull { it.id }

        if (requestedIds.size != requestedIds.distinct().size) {
            throw BusinessException(ErrorCode.TODO_DUPLICATE_ID)
        }

        val requestedTodos = todoRepository.findAllByIds(requestedIds)
        val requestedTodosById = requestedTodos.associateBy { it.id!! }
        val missingTodoIds = requestedIds - requestedTodosById.keys

        if (missingTodoIds.isNotEmpty()) {
            throw BusinessException(ErrorCode.TODO_NOT_FOUND)
        }

        requestedTodos.forEach { todo ->
            todo.validateOwner(loginId)
            if (todo.isDifferentLink(linkId)) {
                throw BusinessException(ErrorCode.TODO_DIFFERENT_LINK)
            }
        }

        val validatedTodosById =
            requestedIds.associateWith { todoId -> requestedTodosById.getValue(todoId) }

        val todosToUpdate =
            todos
                .mapNotNull { todoRequest ->
                    todoRequest.id?.let { todoId ->
                        validatedTodosById.getValue(todoId).update(
                            linkId = linkId,
                            title = todoRequest.title,
                            reminderAt = todoRequest.reminderAt,
                            repeatUntil = todoRequest.repeatUntil,
                            repeatDays = todoRequest.repeatDays,
                            repeatTime = todoRequest.repeatTime,
                            repeatTimezone = todoRequest.repeatTimezone,
                            now = Clock.System.now(),
                        )
                    }
                }

        val todosToCreate =
            todos
                .filter { it.id == null }
                .map { todoRequest ->
                    todoRequest.toTodo(
                        linkId = linkId,
                        ownerId = loginId,
                    )
                }

        val todoIdsToDelete = existingTodos.mapNotNull { it.id }.filter { it !in requestedIds.toSet() }

        return TodoChangeSet(
            todosToUpdate = todosToUpdate,
            todosToCreate = todosToCreate,
            todoIdsToDelete = todoIdsToDelete,
        )
    }

    private fun Link.hasSamePatchFieldsAs(other: Link): Boolean =
        folderId == other.folderId &&
            memo == other.memo &&
            tags == other.tags

    private data class TodoChangeSet(
        val todosToUpdate: List<Todo>,
        val todosToCreate: List<Todo>,
        val todoIdsToDelete: List<Long>,
    )

    private data class PatchLinkResult(
        val response: PatchLinkResponse,
        val deletedTodoIds: List<Long>,
        val updatedTodos: List<Todo>,
        val createdTodos: List<Todo>,
    )

    private fun PatchLinkTodoRequest.toTodo(
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
