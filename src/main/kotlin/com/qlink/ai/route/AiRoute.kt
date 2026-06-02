package com.qlink.ai.route

import com.qlink.ai.dto.PutAiUserProviderRequest
import com.qlink.ai.service.GetAiProviderModelsService
import com.qlink.ai.service.PutAiUserProviderService
import com.qlink.auth.domain.JwtPrincipal
import com.qlink.common.response.respondSuccess
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.aiRoutes() {
    val getAiProviderModelsService by inject<GetAiProviderModelsService>()
    val putAiUserProviderService by inject<PutAiUserProviderService>()

    authenticate(optional = true) {
        get<AiResources.ProviderModels>(getAiProviderModelsDocs()) {
            val principal = call.principal<JwtPrincipal>()
            val response = getAiProviderModelsService.getAiProviderModels(principal?.userId)

            call.respondSuccess(HttpStatusCode.OK, response)
        }
    }

    authenticate {
        put<AiResources.UserProviders>(putAiUserProviderDocs()) {
            val principal = call.principal<JwtPrincipal>()!!
            val request = call.receive<PutAiUserProviderRequest>()
            val response = putAiUserProviderService.putAiUserProvider(principal.userId, request)

            call.respondSuccess(HttpStatusCode.Created, response)
        }
    }
}
