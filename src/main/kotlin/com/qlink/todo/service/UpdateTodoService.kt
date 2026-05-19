package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository
import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.UpdateTodoRequest
import com.qlink.todo.dto.UpdateTodoResponse
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository

class UpdateTodoService(
    private val tx: TransactionRunner,
    private val todoRepository: TodoRepository,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
) {
    suspend fun updateTodo(
        loginId: Long,
        todoId: Long,
        request: UpdateTodoRequest,
    ): UpdateTodoResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

            val todo = todoRepository.findById(todoId) ?: throw BusinessException(ErrorCode.TODO_NOT_FOUND)
            todo.validateOwner(loginId)

            if (todo.isDifferentLink(request.linkId)) {
                linkRepository
                    .findById(request.linkId)
                    ?.also { it.validateOwner(loginId) }
                    ?: throw BusinessException(ErrorCode.TODO_LINK_NOT_FOUND)
            }

            val updatedTodo =
                todo.update(
                    linkId = request.linkId,
                    title = request.title,
                    reminderAt = request.reminderAt,
                )

            todoRepository.update(updatedTodo).toResponse()
        }

    private fun Todo.toResponse(): UpdateTodoResponse =
        UpdateTodoResponse(
            linkId = linkId,
            title = title,
            reminderAt = reminderAt,
        )
}
