package com.qlink.todo.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.notification.service.ScheduleTodoNotificationService
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository

class DeleteTodoService(
    private val tx: TransactionRunner,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
) {
    suspend fun deleteTodo(
        loginId: Long,
        todoId: Long,
    ) {
        val exists =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

                val todo = todoRepository.findById(todoId) ?: return@required false

                todo.validateOwner(loginId)
                true
            }

        if (!exists) {
            return
        }

        scheduleTodoNotificationService.cancelForTodo(todoId)

        tx.required {
            todoRepository.deleteById(todoId)
        }
    }
}
