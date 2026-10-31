package com.qlink.link.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.link.dto.CreateLinkPreviewRequest
import com.qlink.link.service.CreateLinkPreviewService
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.linkPreviewRoutes() {
    val createLinkPreviewService by inject<CreateLinkPreviewService>()

    authenticate {
        post<LinkPreviewResources>(createLinkPreviewDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateLinkPreviewRequest>()
            val response = createLinkPreviewService.createPreview(principal.userId, request.url)
            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}
