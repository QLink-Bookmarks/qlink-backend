package com.qlink.user.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import com.qlink.user.dto.UpdateMySettingsRequest
import com.qlink.user.service.GetMyProfileService
import com.qlink.user.service.GetMySettingsService
import com.qlink.user.service.UpdateMySettingsService
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.patch
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val getMyProfileService by inject<GetMyProfileService>()
    val getMySettingsService by inject<GetMySettingsService>()
    val updateMySettingsService by inject<UpdateMySettingsService>()

    authenticate {
        get<UserResources.Me>(getMyProfileDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val response = getMyProfileService.getMyProfile(principal.userId)

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        get<UserResources.Me.Settings>(getMySettingsDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val response = getMySettingsService.getMySettings(principal.userId)

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        patch<UserResources.Me.Settings>(updateMySettingsDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<UpdateMySettingsRequest>()

            updateMySettingsService.updateMySettings(principal.userId, request)

            call.respondSuccess(HttpStatusCode.OK)
        }
    }
}
