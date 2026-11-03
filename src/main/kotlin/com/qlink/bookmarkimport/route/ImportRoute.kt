package com.qlink.bookmarkimport.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.bookmarkimport.dto.CreateImportPreviewRequest
import com.qlink.bookmarkimport.dto.CreateImportRequest
import com.qlink.bookmarkimport.dto.CreateImportReversalRequest
import com.qlink.bookmarkimport.service.CreateImportPreviewService
import com.qlink.bookmarkimport.service.CreateImportReversalService
import com.qlink.bookmarkimport.service.CreateImportService
import com.qlink.bookmarkimport.service.GetImportDetailService
import com.qlink.bookmarkimport.service.GetImportReversalPreviewService
import com.qlink.bookmarkimport.service.GetImportsService
import com.qlink.common.response.respondSuccess
import com.qlink.common.scroll.ScrollRequest
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.importRoutes() {
    val createImportPreviewService by inject<CreateImportPreviewService>()
    val createImportService by inject<CreateImportService>()
    val getImportsService by inject<GetImportsService>()
    val getImportDetailService by inject<GetImportDetailService>()
    val getImportReversalPreviewService by inject<GetImportReversalPreviewService>()
    val createImportReversalService by inject<CreateImportReversalService>()

    authenticate {
        post<ImportResources.Previews>(createImportPreviewDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateImportPreviewRequest>()
            val response = createImportPreviewService.createPreview(principal.userId, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }

        post<ImportResources.Imports>(createImportDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateImportRequest>()
            val response = createImportService.createImport(principal.userId, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }

        get<ImportResources.Imports>(getImportsDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val response =
                getImportsService.getImports(
                    loginId = principal.userId,
                    scrollRequest = ScrollRequest(cursor = resource.cursor, size = resource.size),
                )

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        get<ImportResources.Imports.ById>(getImportDetailDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val response = getImportDetailService.getImportDetail(principal.userId, resource.id)

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        get<ImportResources.Imports.ById.ReversalPreview>(getImportReversalPreviewDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val response = getImportReversalPreviewService.getReversalPreview(principal.userId, resource.parent.id)

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        post<ImportResources.Imports.ById.Reversals>(createImportReversalDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<CreateImportReversalRequest>()
            val response = createImportReversalService.createReversal(principal.userId, resource.parent.id, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}
