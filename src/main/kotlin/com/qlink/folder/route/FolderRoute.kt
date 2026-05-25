package com.qlink.folder.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.common.scroll.ScrollRequest
import com.qlink.folder.dto.CreateFolderRequest
import com.qlink.folder.dto.UpdateFolderRequest
import com.qlink.folder.service.CreateFolderService
import com.qlink.folder.service.DeleteFolderService
import com.qlink.folder.service.GetFoldersService
import com.qlink.folder.service.UpdateFolderService
import io.github.smiley4.ktoropenapi.resources.delete
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.put
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.folderRoutes() {
    val createFolderService by inject<CreateFolderService>()
    val deleteFolderService by inject<DeleteFolderService>()
    val getFoldersService by inject<GetFoldersService>()
    val updateFolderService by inject<UpdateFolderService>()

    authenticate {
        get<FolderResources>(getFoldersDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val response =
                getFoldersService.getFolders(
                    loginId = principal.userId,
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

        post<FolderResources>(createFolderDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateFolderRequest>()
            val response = createFolderService.createFolder(principal.userId, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }

        put<FolderResources.ById>(updateFolderDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<UpdateFolderRequest>()
            val response = updateFolderService.updateFolder(principal.userId, resource.id, request)

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        delete<FolderResources.ById>(deleteFolderDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            deleteFolderService.deleteFolder(principal.userId, resource.id, resource.onDelete)

            call.respondSuccess(HttpStatusCode.OK)
        }
    }
}
