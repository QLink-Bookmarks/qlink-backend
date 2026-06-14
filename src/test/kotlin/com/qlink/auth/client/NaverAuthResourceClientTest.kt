package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class NaverAuthResourceClientTest :
    BehaviorSpec({
        fun client(
            content: String,
            status: HttpStatusCode = HttpStatusCode.OK,
            onRequest: (String?) -> Unit = {},
        ): NaverAuthResourceClient =
            NaverAuthResourceClient(
                httpClient =
                    HttpClient(MockEngine) {
                        expectSuccess = false
                        install(ContentNegotiation) {
                            json(Json { ignoreUnknownKeys = true })
                        }
                        engine {
                            addHandler { request ->
                                onRequest(request.headers[HttpHeaders.Authorization])
                                respond(
                                    content = content,
                                    status = status,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        }
                    },
            )

        Given("네이버 userinfo 응답이 주어졌을 때") {
            When("정상 응답이면") {
                var capturedAuthorization: String? = null
                val resource =
                    client(
                        content = """{"resultcode":"00","message":"success","response":{"id":"naver-abc","email":"user@naver.com"}}""",
                        onRequest = { capturedAuthorization = it },
                    ).getResource("naver-access-token")

                Then("중첩된 response에서 providerId를 추출하고 bearer 토큰으로 요청한다") {
                    resource.providerType shouldBe AuthProviderType.NAVER
                    resource.providerId shouldBe "naver-abc"
                    capturedAuthorization shouldBe "Bearer naver-access-token"
                }
            }

            When("2xx가 아닌 응답이면") {
                val naverClient =
                    client(
                        content = """{"resultcode":"024","message":"Authentication failed"}""",
                        status = HttpStatusCode.Unauthorized,
                    )

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED.message) {
                        naverClient.getResource("token")
                    }
                }
            }

            When("응답 본문에 response가 없으면") {
                val naverClient = client(content = """{"resultcode":"024","message":"Authentication failed"}""")

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED.message) {
                        naverClient.getResource("token")
                    }
                }
            }
        }
    })
