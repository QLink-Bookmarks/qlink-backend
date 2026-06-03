package com.qlink.todo.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.service.ScheduleTodoNotificationService
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
    private val scheduleTodoNotificationService: ScheduleTodoNotificationService,
) {
    suspend fun createTodo(
        loginId: Long,
        request: CreateTodoRequest,
    ): CreateTodoResponse {
        val savedTodo =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.TODO_OWNER_NOT_FOUND)

                linkRepository
                    .findById(request.linkId)
                    ?.also { it.validateOwner(loginId) }
                    ?: throw BusinessException(ErrorCode.TODO_LINK_NOT_FOUND)

                val todo =
                    Todo.create(
                        linkId = request.linkId,
                        ownerId = loginId,
                        title = request.title,
                        reminderAt = request.reminderAt,
                        repeatUntil = request.repeatUntil,
                        repeatDays = request.repeatDays,
                        repeatTime = request.repeatTime,
                        repeatTimezone = request.repeatTimezone,
                        now = Clock.System.now(),
                    )

                todoRepository.insert(todo)
            }

        scheduleTodoNotificationService.createForTodo(savedTodo)

        return CreateTodoResponse(savedTodo.id!!)
    }
}
