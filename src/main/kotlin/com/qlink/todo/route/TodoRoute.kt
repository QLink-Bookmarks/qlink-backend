package com.qlink.todo.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.todo.dto.CreateTodoRequest
import com.qlink.todo.service.CreateTodoService
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.todoRoutes() {
    val createTodoService by inject<CreateTodoService>()

    authenticate {
        post<TodoResources>(createTodoDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateTodoRequest>()
            val response = createTodoService.createTodo(principal.userId, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}
