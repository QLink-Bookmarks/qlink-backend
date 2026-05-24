package com.qlink.folder.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.folder.dto.CreateFolderRequest
import com.qlink.folder.service.CreateFolderService
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.folderRoutes() {
    val createFolderService by inject<CreateFolderService>()

    authenticate {
        post<FolderResources>(createFolderDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateFolderRequest>()
            val response = createFolderService.createFolder(principal.userId, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}
