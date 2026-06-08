package com.qlink.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.SecurityConfig
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.Base64

class AuthTokenServiceTest :
    BehaviorSpec({
        Given("auth token service가 있을 때") {
            val securityConfig = SecurityConfig(jwtSecret = "test-jwt-secret")
            val service = AuthTokenService(securityConfig = securityConfig)
            val algorithm = Algorithm.HMAC256(securityConfig.jwtSecret)

            When("access token을 발급하면") {
                val userId = RandomFixture.randomId()
                val accessToken =
                    service.issueAccessToken(
                        userId = userId,
                        role = Role.NORMAL,
                    )
                val decoded =
                    JWT
                        .require(algorithm)
                        .build()
                        .verify(accessToken)

                Then("사용자와 role claim이 포함된다") {
                    decoded.subject shouldBe userId.toString()
                    decoded.getClaim("role").asString() shouldBe Role.NORMAL.name
                }
            }

            When("refresh token을 발급하고 검증하면") {
                val userId = RandomFixture.randomId()
                val familyId = "family-${RandomFixture.randomId()}"
                val refreshToken =
                    service.issueRefreshToken(
                        userId = userId,
                        familyId = familyId,
                    )
                val claims = service.verifyRefreshToken(refreshToken)

                Then("사용자와 family id를 복원한다") {
                    claims shouldBe RefreshTokenClaims(userId = userId, familyId = familyId)
                }
            }

            When("base64가 아닌 refresh token이면") {
                val verify = {
                    service.verifyRefreshToken("not-base64")
                }

                Then("인증 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_INVALID_CREDENTIALS.message) {
                        verify()
                    }
                }
            }

            When("refresh token jwt에 user id가 없으면") {
                val token =
                    JWT
                        .create()
                        .withClaim("familyId", "family-id")
                        .sign(algorithm)
                        .toRefreshToken()
                val verify = {
                    service.verifyRefreshToken(token)
                }

                Then("인증 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_INVALID_CREDENTIALS.message) {
                        verify()
                    }
                }
            }

            When("refresh token jwt에 family id가 없으면") {
                val token =
                    JWT
                        .create()
                        .withSubject(RandomFixture.randomId().toString())
                        .sign(algorithm)
                        .toRefreshToken()
                val verify = {
                    service.verifyRefreshToken(token)
                }

                Then("인증 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_INVALID_CREDENTIALS.message) {
                        verify()
                    }
                }
            }
        }
    })

private fun String.toRefreshToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(Charsets.UTF_8))
