package com.qlink.auth.client

import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.AppleConfig
import com.qlink.support.AppleTestKeys
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

class AppleAuthResourceClientTest :
    BehaviorSpec({
        fun client(
            jwks: String = AppleTestKeys.jwks(),
            status: HttpStatusCode = HttpStatusCode.OK,
            clientIds: List<String> = listOf(AppleTestKeys.CLIENT_ID),
        ): AppleAuthResourceClient =
            AppleAuthResourceClient(
                httpClient =
                    HttpClient(MockEngine) {
                        expectSuccess = false
                        install(ContentNegotiation) {
                            json(Json { ignoreUnknownKeys = true })
                        }
                        engine {
                            addHandler {
                                respond(
                                    content = jwks,
                                    status = status,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        }
                    },
                appleConfig = AppleConfig(clientIds = clientIds),
            )

        Given("애플 id_token 검증 테스트") {
            When("유효한 id_token이면") {
                val sub = "apple-user-1234"
                val resource = client().getResource(AppleTestKeys.idToken(subject = sub), AuthPlatform.NATIVE)

                Then("JWKS 공개키로 검증하고 sub를 providerId로 추출한다") {
                    resource.providerType shouldBe AuthProviderType.APPLE
                    resource.providerId shouldBe sub
                }
            }

            When("aud가 허용된 client id 목록에 없으면") {
                val appleClient = client()
                val token = AppleTestKeys.idToken(subject = "u", audience = "com.someone.else")

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        appleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("만료된 토큰이면") {
                val appleClient = client()
                val token =
                    AppleTestKeys.idToken(
                        subject = "u",
                        expiresAt = Date(System.currentTimeMillis() - 3_600_000),
                    )

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        appleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("issuer가 애플이 아니면") {
                val appleClient = client()
                val token = AppleTestKeys.idToken(subject = "u", issuer = "https://evil.example.com")

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        appleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("JWKS 공개키와 다른 키로 서명되었으면") {
                val appleClient = client()
                val token = AppleTestKeys.idToken(subject = "u", signWithWrongKey = true)

                Then("서명 검증 실패로 외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        appleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("토큰의 kid가 JWKS에 없으면") {
                val appleClient = client(jwks = AppleTestKeys.jwks(keyId = "other-kid"))
                val token = AppleTestKeys.idToken(subject = "u")

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        appleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }

            When("JWKS 응답이 2xx가 아니면") {
                val appleClient = client(status = HttpStatusCode.InternalServerError)
                val token = AppleTestKeys.idToken(subject = "u")

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_COMMUNICATION_FAILED.message) {
                        appleClient.getResource(token, AuthPlatform.NATIVE)
                    }
                }
            }
        }
    })
