package com.qlink.todo.route

import io.ktor.resources.Resource

@Resource("/todos")
class TodoResources {
    @Resource("{id}")
    class ById(
        val parent: TodoResources = TodoResources(),
        val id: Long,
    )
}
