package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.notification.service.ScheduleTodoNotificationService
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
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
) {
    suspend fun completeTodo(
        loginId: Long,
        todoId: Long,
        request: CompleteTodoRequest,
    ): CompleteTodoResponse {
        val result =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

                val todo = todoRepository.findById(todoId) ?: throw BusinessException(ErrorCode.TODO_NOT_FOUND)
                todo.validateOwner(loginId)

                if (todo.isCompleted == request.isCompleted) {
                    return@required CompleteTodoResult(todo = todo, changed = false)
                }

                val completedTodo = todo.completeBy(request.isCompleted)

                CompleteTodoResult(
                    todo = todoRepository.update(completedTodo),
                    changed = true,
                )
            }

        if (result.changed) {
            if (result.todo.isCompleted) {
                scheduleTodoNotificationService.cancelForTodo(result.todo.id!!)
            } else {
                scheduleTodoNotificationService.replaceForTodo(result.todo)
            }
        }

        return result.todo.toResponse()
    }

    private data class CompleteTodoResult(
        val todo: Todo,
        val changed: Boolean,
    )

    private fun Todo.completeBy(isCompleted: Boolean): Todo =
        if (isCompleted) {
            complete(Clock.System.now())
        } else {
            incomplete()
        }

    private fun Todo.toResponse(): CompleteTodoResponse =
        CompleteTodoResponse(
            completeAt = completedAt,
        )
}
