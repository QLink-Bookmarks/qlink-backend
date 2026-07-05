package com.qlink.post.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.post.dto.CreatePostRequest
import com.qlink.post.service.CreatePostService
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.postRoutes() {
    val createPostService by inject<CreatePostService>()

    authenticate {
        post<PostResources>(createPostDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreatePostRequest>()
            val response = createPostService.createPost(principal.userId, principal.role, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}
