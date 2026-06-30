package com.qlink.notification.service

import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.domain.Notification
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.notification.worker.TaskScheduler
import com.qlink.todo.domain.Todo

class ScheduleTodoNotificationService(
    private val tx: TransactionRunner,
    private val notificationRepository: NotificationRepository,
    private val linkRepository: LinkRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend fun createForTodo(todo: Todo) {
        val notification =
            tx.required {
                buildTodoNotification(todo)?.let { notificationRepository.insert(it) }
            }

        notification?.let { taskScheduler.scheduleIfToday(it) }
    }

    suspend fun replaceForTodo(todo: Todo) {
        val result =
            tx.required {
                val todoId = todo.id ?: return@required ScheduleTodoNotificationResult()
                val deletedIds =
                    notificationRepository.deletePendingByContext(
                        context = NotificationContext.TODO,
                        contextId = todoId,
                    )
                val notification = buildTodoNotification(todo)?.let { notificationRepository.insert(it) }

                ScheduleTodoNotificationResult(
                    deletedIds = deletedIds,
                    notification = notification,
                )
            }

        result.deletedIds.forEach(taskScheduler::cancel)
        result.notification?.let { taskScheduler.scheduleIfToday(it) }
    }

    private suspend fun buildTodoNotification(todo: Todo): Notification? {
        val link = linkRepository.findById(todo.linkId) ?: return null

        return Notification.todo(todo = todo, linkTitle = link.title, linkUrl = link.url)
    }

    suspend fun cancelForTodo(todoId: Long) {
        val deletedIds =
            tx.required {
                notificationRepository.deletePendingByContext(
                    context = NotificationContext.TODO,
                    contextId = todoId,
                )
            }

        deletedIds.forEach(taskScheduler::cancel)
    }

    private data class ScheduleTodoNotificationResult(
        val deletedIds: List<Long> = emptyList(),
        val notification: Notification? = null,
    )
}
