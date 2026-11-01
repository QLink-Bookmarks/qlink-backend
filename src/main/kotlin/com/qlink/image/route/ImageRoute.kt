package com.qlink.image.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.image.service.UploadImageService
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

private const val IMAGE_PART_NAME = "image"

fun Route.imageRoutes() {
    val uploadImageService by inject<UploadImageService>()

    authenticate {
        post<ImageResources>(uploadImageDocs()) { resource ->
            val principal = call.principal<JwtPrincipal>()!!
            val response =
                uploadImageService.upload(
                    loginId = principal.userId,
                    purpose = resource.purpose,
                    url = resource.url,
                    bytes = call.receiveImageBytes(),
                )

            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}

private suspend fun ApplicationCall.receiveImageBytes(): ByteArray? {
    if (!request.contentType().match(ContentType.MultiPart.FormData)) return null

    var bytes: ByteArray? = null

    receiveMultipart().forEachPart { part ->
        if (part is PartData.FileItem && part.name == IMAGE_PART_NAME && bytes == null) {
            bytes = part.provider().readRemaining().readByteArray()
        }
        part.dispose()
    }

    return bytes
}
