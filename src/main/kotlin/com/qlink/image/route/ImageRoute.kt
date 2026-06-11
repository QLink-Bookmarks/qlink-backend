package com.qlink.image.route

import com.qlink.common.response.respondSuccess
import com.qlink.image.service.UploadImageService
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

private const val IMAGE_PART_NAME = "image"

fun Route.imageRoutes() {
    val uploadImageService by inject<UploadImageService>()

    post<ImageResources>(uploadImageDocs()) {
        val bytes = call.receiveImageBytes()
        val response = uploadImageService.upload(bytes)

        call.respondSuccess(HttpStatusCode.Created, response)
    }
}

private suspend fun ApplicationCall.receiveImageBytes(): ByteArray? {
    var bytes: ByteArray? = null

    receiveMultipart().forEachPart { part ->
        if (part is PartData.FileItem && part.name == IMAGE_PART_NAME && bytes == null) {
            bytes = part.provider().readRemaining().readByteArray()
        }
        part.dispose()
    }

    return bytes
}
