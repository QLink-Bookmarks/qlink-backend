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

class GoogleAuthResourceClientTest :
    BehaviorSpec({
        fun client(
            content: String,
            status: HttpStatusCode = HttpStatusCode.OK,
            onRequest: (String?) -> Unit = {},
        ): GoogleAuthResourceClient =
            GoogleAuthResourceClient(
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

        Given("구글 userinfo 응답이 주어졌을 때") {
            When("정상 응답이면") {
                var capturedAuthorization: String? = null
                val resource =
                    client(
                        content = """{"sub":"google-123","email":"user@example.com"}""",
                        onRequest = { capturedAuthorization = it },
                    ).getResource("google-access-token")

                Then("sub로 providerId를 추출하고 bearer 토큰으로 요청한다") {
                    resource.providerType shouldBe AuthProviderType.GOOGLE
                    resource.providerId shouldBe "google-123"
                    capturedAuthorization shouldBe "Bearer google-access-token"
                }
            }

            When("2xx가 아닌 응답이면") {
                val googleClient =
                    client(
                        content = """{"error":"invalid_token"}""",
                        status = HttpStatusCode.Unauthorized,
                    )

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED.message) {
                        googleClient.getResource("token")
                    }
                }
            }

            When("응답 본문에 sub가 없으면") {
                val googleClient = client(content = """{"email":"user@example.com"}""")

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED.message) {
                        googleClient.getResource("token")
                    }
                }
            }
        }
    })
