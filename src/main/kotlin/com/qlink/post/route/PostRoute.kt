package com.qlink.post.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.common.scroll.ScrollRequest
import com.qlink.plugin.jwtPrincipalOrGuest
import com.qlink.post.dto.CreatePostRequest
import com.qlink.post.service.CreatePostService
import com.qlink.post.service.GetPostService
import com.qlink.post.service.GetPostsService
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.postRoutes() {
    val createPostService by inject<CreatePostService>()
    val getPostService by inject<GetPostService>()
    val getPostsService by inject<GetPostsService>()

    authenticate {
        post<PostResources>(createPostDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreatePostRequest>()
            val response = createPostService.createPost(principal.userId, principal.role, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }

    authenticate(optional = true) {
        get<PostResources>(getPostsDocs()) { resource ->
            val principal = call.jwtPrincipalOrGuest()
            val response =
                getPostsService.getPosts(
                    role = principal.role,
                    type = resource.type,
                    query = resource.query,
                    order = resource.order,
                    scrollRequest =
                        ScrollRequest(
                            cursor = resource.cursor,
                            size = resource.size,
                        ),
                )

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        get<PostResources.ById>(getPostDocs()) { resource ->
            val principal = call.jwtPrincipalOrGuest()
            val response = getPostService.getPost(resource.id, principal.role)

            call.respondSuccess(HttpStatusCode.OK, response)
        }
    }
}
