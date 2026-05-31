package com.qlink.todo.route

import com.qlink.todo.dto.DEFAULT_TODO_SCROLL_SIZE
import com.qlink.todo.dto.DEFAULT_TODO_SEARCH_ORDER
import io.ktor.resources.Resource

@Resource("/todos")
class TodoResources(
    val order: String = DEFAULT_TODO_SEARCH_ORDER,
    val cursor: String? = null,
    val size: Int = DEFAULT_TODO_SCROLL_SIZE,
    val isCompleted: Boolean? = null,
    val reminderAt: String? = null,
) {
    @Resource("{id}")
    class ById(
        val parent: TodoResources = TodoResources(),
        val id: Long,
    ) {
        @Resource("completed")
        class Completed(
            val parent: ById,
        )
    }
}
