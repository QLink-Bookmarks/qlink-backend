package com.qlink.todo.service

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.todo.domain.TodoStatus
import com.qlink.todo.dto.GetTodoCountsResponse
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class GetTodoCountsService(
    private val tx: TransactionRunner,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getTodoCounts(loginId: Long): GetTodoCountsResponse =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

            val now = Clock.System.now()
            val counts = todoRepository.findAllByOwnerId(loginId).groupingBy { it.status(now) }.eachCount()

            GetTodoCountsResponse(
                all = counts.values.sum(),
                active = counts[TodoStatus.ACTIVE] ?: 0,
                overdue = counts[TodoStatus.OVERDUE] ?: 0,
                completed = counts[TodoStatus.COMPLETED] ?: 0,
            )
        }
}
