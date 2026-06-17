package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.GoogleConfig
import com.qlink.support.GoogleTestKeys
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
import java.util.Date

class GoogleAuthResourceClientTest :
    BehaviorSpec({
        fun client(
            content: String,
            status: HttpStatusCode = HttpStatusCode.OK,
            clientIds: List<String> = listOf(GoogleTestKeys.CLIENT_ID),
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
                googleConfig = GoogleConfig(clientIds = clientIds),
            )

        Given("WEB 플랫폼 - access token으로 userinfo를 조회할 때") {
            When("정상 응답이면") {
                var capturedAuthorization: String? = null
                val resource =
                    client(
                        content = """{"sub":"google-123","email":"user@example.com"}""",
                        onRequest = { capturedAuthorization = it },
                    ).getResource("google-access-token", AuthPlatform.WEB)

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

                Then("토큰 무효 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        googleClient.getResource("token", AuthPlatform.WEB)
                    }
                }
            }

            When("응답 본문에 sub가 없으면") {
                val googleClient = client(content = """{"email":"user@example.com"}""")

                Then("통신 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED.message) {
                        googleClient.getResource("token", AuthPlatform.WEB)
                    }
                }
            }
        }

        Given("NATIVE 플랫폼 - id_token을 JWKS로 검증할 때") {
            When("유효한 id_token이면") {
                val sub = "google-native-1234"
                val resource =
                    client(content = GoogleTestKeys.jwks())
                        .getResource(GoogleTestKeys.idToken(subject = sub), AuthPlatform.NATIVE)

                Then("구글 공개키로 검증하고 sub를 providerId로 추출한다") {
                    resource.providerType shouldBe AuthProviderType.GOOGLE
                    resource.providerId shouldBe sub
                }
            }

            When("aud가 허용된 client id 목록에 없으면") {
                val googleClient = client(content = GoogleTestKeys.jwks())
                val token = GoogleTestKeys.idToken(subject = "u", audience = "other.apps.googleusercontent.com")

                Then("토큰 무효 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        googleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("만료된 id_token이면") {
                val googleClient = client(content = GoogleTestKeys.jwks())
                val token =
                    GoogleTestKeys.idToken(
                        subject = "u",
                        expiresAt = Date(System.currentTimeMillis() - 3_600_000),
                    )

                Then("토큰 무효 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        googleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("issuer가 구글이 아니면") {
                val googleClient = client(content = GoogleTestKeys.jwks())
                val token = GoogleTestKeys.idToken(subject = "u", issuer = "https://evil.example.com")

                Then("토큰 무효 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        googleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("JWKS 공개키와 다른 키로 서명되었으면") {
                val googleClient = client(content = GoogleTestKeys.jwks())
                val token = GoogleTestKeys.idToken(subject = "u", signWithWrongKey = true)

                Then("토큰 무효 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        googleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("JWKS 응답이 2xx가 아니면") {
                val googleClient = client(content = GoogleTestKeys.jwks(), status = HttpStatusCode.InternalServerError)
                val token = GoogleTestKeys.idToken(subject = "u")

                Then("통신 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED.message) {
                        googleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }
        }
    })
