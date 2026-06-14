@file:Suppress("ktlint:standard:filename")

package com.qlink.support

import com.qlink.auth.client.AuthResource
import com.qlink.auth.client.AuthResourceClient
import com.qlink.auth.domain.AuthProviderType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 외부 HTTP 요청을 모킹하는 테스트용 엔진. 실제 Google/Naver 인증 클라이언트를
 * 이 엔진의 [client]로 구성해, userinfo API 응답만 갈아끼우며 테스트한다.
 */
class MockAuthHttpEngine {
    private var responder: () -> Pair<String, HttpStatusCode> = { "{}" to HttpStatusCode.NotFound }

    val client: HttpClient =
        HttpClient(MockEngine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    val (body, status) = responder()
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }

    fun respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        responder = { body to status }
    }

    fun reset() {
        responder = { "{}" to HttpStatusCode.NotFound }
    }
}

class FakeAuthResourceClient : AuthResourceClient {
    override val providerType: AuthProviderType = AuthProviderType.KAKAO
    var providerId: String = "kakao-user"
    var failure: Throwable? = null
    val requestedTokens: MutableList<String> = mutableListOf()

    override suspend fun getResource(token: String): AuthResource {
        requestedTokens.add(token)
        failure?.let { throw it }

        return AuthResource(
            providerType = providerType,
            providerId = providerId,
        )
    }

    fun reset() {
        providerId = "kakao-user"
        failure = null
        requestedTokens.clear()
    }
}
