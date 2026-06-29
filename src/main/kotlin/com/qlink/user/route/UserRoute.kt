package com.qlink.user.route

import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.cookie.expireAuthCookies
import com.qlink.common.response.respondSuccess
import com.qlink.user.dto.UpdateMyAgreementsRequest
import com.qlink.user.dto.UpdateMyProfileRequest
import com.qlink.user.dto.UpdateMySettingsRequest
import com.qlink.user.service.DeleteAccountService
import com.qlink.user.service.GetMyProfileService
import com.qlink.user.service.GetMySettingsService
import com.qlink.user.service.UpdateMyAgreementsService
import com.qlink.user.service.UpdateMyProfileService
import com.qlink.user.service.UpdateMySettingsService
import io.github.smiley4.ktoropenapi.resources.delete
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.patch
import io.github.smiley4.ktoropenapi.resources.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val getMyProfileService by inject<GetMyProfileService>()
    val getMySettingsService by inject<GetMySettingsService>()
    val updateMyProfileService by inject<UpdateMyProfileService>()
    val updateMySettingsService by inject<UpdateMySettingsService>()
    val updateMyAgreementsService by inject<UpdateMyAgreementsService>()
    val deleteAccountService by inject<DeleteAccountService>()

    authenticate {
        get<UserResources.Me>(getMyProfileDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val response = getMyProfileService.getMyProfile(principal.userId)

            call.respondSuccess(HttpStatusCode.OK, response)
        }

        put<UserResources.Me>(updateMyProfileDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<UpdateMyProfileRequest>()

            updateMyProfileService.updateMyProfile(principal.userId, request)

            call.respondSuccess(HttpStatusCode.OK)
        }

        delete<UserResources.Me>(deleteMyAccountDocs()) {
            val principal = call.principal<JwtPrincipal>()!!

            deleteAccountService.deleteAccount(principal.userId)

            call.expireAuthCookies()
            call.respondSuccess(HttpStatusCode.OK)
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

        put<UserResources.Me.Agreements>(updateMyAgreementsDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<UpdateMyAgreementsRequest>()

            updateMyAgreementsService.updateMyAgreements(principal.userId, request)

            call.respondSuccess(HttpStatusCode.OK)
        }
    }
}
