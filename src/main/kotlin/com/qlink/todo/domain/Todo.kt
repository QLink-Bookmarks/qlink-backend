package com.qlink.todo.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import kotlin.time.Instant

private const val MAX_TITLE_LENGTH = 50

class Todo(
    val id: Long? = null,
    val linkId: Long,
    val ownerId: Long,
    val title: String,
    val reminderAt: Instant? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        validateTitle(title)
    }

    val isCompleted: Boolean
        get() = completedAt != null

    fun validateOwner(ownerId: Long) {
        if (this.ownerId != ownerId) {
            throw BusinessException(ErrorCode.TODO_DIFFERENT_OWNER)
        }
    }

    fun update(
        linkId: Long,
        title: String,
        reminderAt: Instant?,
    ): Todo =
        Todo(
            id = id,
            linkId = linkId,
            ownerId = ownerId,
            title = title,
            reminderAt = reminderAt,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun complete(completedAt: Instant): Todo =
        if (isCompleted) {
            this
        } else {
            Todo(
                id = id,
                linkId = linkId,
                ownerId = ownerId,
                title = title,
                reminderAt = reminderAt,
                completedAt = completedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

    fun incomplete(): Todo =
        if (!isCompleted) {
            this
        } else {
            Todo(
                id = id,
                linkId = linkId,
                ownerId = ownerId,
                title = title,
                reminderAt = reminderAt,
                completedAt = null,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

    private fun validateTitle(title: String) {
        title.isNotBlank().requireTrue(ErrorCode.TODO_TITLE_BLANK)
        title.requireNotOver(MAX_TITLE_LENGTH, ErrorCode.TODO_TITLE_OVER_MAX)
    }
}
