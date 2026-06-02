package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository
import com.qlink.todo.domain.Todo
import com.qlink.todo.dto.CreateTodoRequest
import com.qlink.todo.dto.CreateTodoResponse
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class CreateTodoService(
    private val tx: TransactionRunner,
    private val todoRepository: TodoRepository,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
) {
    suspend fun createTodo(
        loginId: Long,
        request: CreateTodoRequest,
    ): CreateTodoResponse =
        tx.required {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

            linkRepository
                .findById(request.linkId)
                ?.also { it.validateOwner(loginId) }
                ?: throw BusinessException(ErrorCode.TODO_LINK_NOT_FOUND)

            val repeatTime = Todo.parseRepeatTime(request.repeatTime)
            val todo =
                Todo(
                    linkId = request.linkId,
                    ownerId = loginId,
                    title = request.title,
                    reminderAt = request.reminderAt.takeIf { !request.hasCompleteRepeat() },
                    repeatUntil = request.repeatUntil,
                    repeatDays = request.repeatDays,
                    repeatTime = repeatTime,
                    repeatTimezone =
                        Todo.normalizeRepeatTimezone(
                            repeatUntil = request.repeatUntil,
                            repeatDays = request.repeatDays,
                            repeatTime = repeatTime,
                            repeatTimezone = request.repeatTimezone,
                        ),
                ).let { if (it.hasRepeat) it.setNextReminder(Clock.System.now()) else it }

            CreateTodoResponse(todoRepository.insert(todo).id!!)
        }

    private fun CreateTodoRequest.hasCompleteRepeat(): Boolean =
        repeatUntil != null && repeatDays != null && repeatTime != null
}
