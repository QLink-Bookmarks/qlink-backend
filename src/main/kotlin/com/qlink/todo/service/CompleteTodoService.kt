package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.CompleteTodoRequest
import com.qlink.todo.dto.CompleteTodoResponse
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class CompleteTodoService(
    private val tx: TransactionRunner,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
) {
    suspend fun completeTodo(
        loginId: Long,
        todoId: Long,
        request: CompleteTodoRequest,
    ): CompleteTodoResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

            val todo = todoRepository.findById(todoId) ?: throw BusinessException(ErrorCode.TODO_NOT_FOUND)
            todo.validateOwner(loginId)

            if (todo.isCompleted == request.isComplete) {
                return@required todo.toResponse()
            }

            val completedTodo = todo.completeBy(request.isComplete)

            todoRepository.update(completedTodo).toResponse()
        }

    private fun Todo.completeBy(isComplete: Boolean): Todo =
        if (isComplete) {
            complete(Clock.System.now())
        } else {
            incomplete()
        }

    private fun Todo.toResponse(): CompleteTodoResponse =
        CompleteTodoResponse(
            completeAt = completedAt,
        )
}
