package com.qlink.todo.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository

class DeleteTodoService(
    private val tx: TransactionRunner,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
) {
    suspend fun deleteTodo(
        loginId: Long,
        todoId: Long,
    ) {
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

            val todo = todoRepository.findById(todoId) ?: return@required

            todo.validateOwner(loginId)
            // TODO: Delete todo notifications before deleting the todo.
            todoRepository.deleteById(todoId)
        }
    }
}
