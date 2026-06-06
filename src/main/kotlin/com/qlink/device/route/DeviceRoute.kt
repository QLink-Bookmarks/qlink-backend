package com.qlink.device.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.device.dto.PutDeviceRequest
import com.qlink.device.service.PutDeviceService
import io.github.smiley4.ktoropenapi.resources.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.deviceRoutes() {
    val putDeviceService by inject<PutDeviceService>()

    authenticate {
        put<DeviceResources>(putDeviceDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<PutDeviceRequest>()
            val response = putDeviceService.putDevice(principal.userId, request)

            call.respondSuccess(HttpStatusCode.OK, response)
        }
    }
}
