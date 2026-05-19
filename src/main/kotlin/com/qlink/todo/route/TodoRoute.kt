package com.qlink.todo.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.todo.dto.CompleteTodoRequest
import com.qlink.todo.dto.CreateTodoRequest
import com.qlink.todo.dto.UpdateTodoRequest
import com.qlink.todo.service.CompleteTodoService
import com.qlink.todo.service.CreateTodoService
import com.qlink.todo.service.DeleteTodoService
import com.qlink.todo.service.UpdateTodoService
import io.github.smiley4.ktoropenapi.resources.delete
import io.github.smiley4.ktoropenapi.resources.post
import io.github.smiley4.ktoropenapi.resources.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.todoRoutes() {
    val createTodoService by inject<CreateTodoService>()
    val updateTodoService by inject<UpdateTodoService>()
    val completeTodoService by inject<CompleteTodoService>()
    val deleteTodoService by inject<DeleteTodoService>()

    authenticate {
        post<TodoResources>(createTodoDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateTodoRequest>()
            val response = createTodoService.createTodo(principal.userId, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }

        put<TodoResources.ById>(updateTodoDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<UpdateTodoRequest>()
            val response = updateTodoService.updateTodo(principal.userId, resource.id, request)

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        put<TodoResources.ById.Completed>(completeTodoDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CompleteTodoRequest>()
            val response = completeTodoService.completeTodo(principal.userId, resource.parent.id, request)

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        delete<TodoResources.ById>(deleteTodoDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            deleteTodoService.deleteTodo(principal.userId, resource.id)

            call.respondSuccess(HttpStatusCode.OK)
        }
    }
}
