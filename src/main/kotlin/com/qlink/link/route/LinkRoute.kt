package com.qlink.link.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.service.CreateLinkService
import com.qlink.link.service.GetLinkDetailService
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.linkRoutes() {
    val createLinkService by inject<CreateLinkService>()
    val getLinkDetailService by inject<GetLinkDetailService>()

    authenticate {
        post<LinkResources>(createLinkDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateLinkRequest>()
            val response = createLinkService.createLink(principal.userId, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }

    authenticate {
        get<LinkResources.ById>(getLinkDetailDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val response = getLinkDetailService.getLinkDetail(principal.userId, resource.id)

            call.respondSuccess(HttpStatusCode.OK, response)
        }
    }
}
