package com.qlink.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qlink.auth.domain.RefreshToken
import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.SecurityConfig
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.Base64
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant

class RefreshAuthTokenServiceTest :
    BaseServiceTest({
        val service = koinGet<RefreshAuthTokenService>()
        val userRepository = koinGet<UserRepository>()
        val refreshTokenRepository = koinGet<RefreshTokenRepository>()
        val authTokenService = koinGet<AuthTokenService>()
        val securityConfig = koinGet<SecurityConfig>()

        Given("토큰 갱신 요청이 주어졌을 때") {
            When("저장된 refresh token이면") {
                val user = insertUser(userRepository)
                val userId = requireNotNull(user.id)
                val familyId = "family-${RandomFixture.randomId()}"
                val currentToken =
                    insertRefreshToken(
                        refreshTokenRepository = refreshTokenRepository,
                        authTokenService = authTokenService,
                        userId = userId,
                        familyId = familyId,
                    ).token

                val response = service.refresh(currentToken)
                val claims = authTokenService.verifyRefreshToken(response.refreshToken)
                val persistedRefreshToken = refreshTokenRepository.findByToken(response.refreshToken)

                Then("새 refresh token으로 회전하고 저장한다") {
                    claims.userId shouldBe userId
                    claims.familyId shouldBe familyId
                    persistedRefreshToken.shouldNotBeNull()
                    persistedRefreshToken.userId shouldBe userId
                }
            }

            When("동시 갱신으로 기존 token이 이미 회전됐고 최신 token이 1분 이내면") {
                val user = insertUser(userRepository)
                val userId = requireNotNull(user.id)
                val familyId = "family-${RandomFixture.randomId()}"
                val requestedToken =
                    issueRefreshTokenAt(
                        securityConfig = securityConfig,
                        userId = userId,
                        familyId = familyId,
                        issuedAt = Clock.System.now() - 1.minutes,
                    )
                val latestToken =
                    insertRefreshToken(
                        refreshTokenRepository = refreshTokenRepository,
                        authTokenService = authTokenService,
                        userId = userId,
                        familyId = familyId,
                    ).token

                val response = service.refresh(requestedToken)

                Then("저장된 최신 refresh token을 응답한다") {
                    response.refreshToken shouldBe latestToken
                }
            }

            When("요청 refresh token이 없으면") {
                val refresh =
                    suspend {
                        service.refresh(null)
                    }

                Then("인증 정보 없음 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_NO_CREDENTIALS.message) {
                        refresh()
                    }
                }
            }

            When("refresh token jwt에 user id가 없으면") {
                val refresh =
                    suspend {
                        service.refresh(missingUserIdRefreshToken(securityConfig))
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_INVALID_CREDENTIALS.message) {
                        refresh()
                    }
                }
            }

            When("refresh token jwt에 family id가 없으면") {
                val refresh =
                    suspend {
                        service.refresh(missingFamilyIdRefreshToken(securityConfig))
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_INVALID_CREDENTIALS.message) {
                        refresh()
                    }
                }
            }

            When("저장된 refresh token이 없으면") {
                val user = insertUser(userRepository)
                val refreshToken =
                    authTokenService.issueRefreshToken(
                        userId = requireNotNull(user.id),
                        familyId = "family-${RandomFixture.randomId()}",
                    )
                val refresh =
                    suspend {
                        service.refresh(refreshToken)
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_INVALID_CREDENTIALS.message) {
                        refresh()
                    }
                }
            }

            When("저장된 최신 refresh token이 1분을 지났으면") {
                val user = insertUser(userRepository)
                val userId = requireNotNull(user.id)
                val familyId = "family-${RandomFixture.randomId()}"
                val requestedToken =
                    issueRefreshTokenAt(
                        securityConfig = securityConfig,
                        userId = userId,
                        familyId = familyId,
                        issuedAt = Clock.System.now() - 3.minutes,
                    )
                insertRefreshToken(
                    refreshTokenRepository = refreshTokenRepository,
                    authTokenService = authTokenService,
                    userId = userId,
                    familyId = familyId,
                    issuedAt = Clock.System.now() - 2.minutes,
                )
                val refresh =
                    suspend {
                        service.refresh(requestedToken)
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_INVALID_CREDENTIALS.message) {
                        refresh()
                    }
                }
            }

            When("저장된 최신 refresh token이 만료됐으면") {
                val user = insertUser(userRepository)
                val userId = requireNotNull(user.id)
                val familyId = "family-${RandomFixture.randomId()}"
                val requestedToken =
                    issueRefreshTokenAt(
                        securityConfig = securityConfig,
                        userId = userId,
                        familyId = familyId,
                        issuedAt = Clock.System.now() - 1.minutes,
                    )
                insertRefreshToken(
                    refreshTokenRepository = refreshTokenRepository,
                    authTokenService = authTokenService,
                    userId = userId,
                    familyId = familyId,
                    issuedAt = Clock.System.now(),
                    expiredAt = Clock.System.now() - 1.minutes,
                )
                val refresh =
                    suspend {
                        service.refresh(requestedToken)
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_INVALID_CREDENTIALS.message) {
                        refresh()
                    }
                }
            }
        }
    })

private suspend fun insertUser(userRepository: UserRepository): User =
    userRepository.insert(
        User(
            username = "user-${RandomFixture.randomId()}",
            nickname = RandomFixture.randomSentenceWithMax(50),
        ),
    )

private suspend fun insertRefreshToken(
    refreshTokenRepository: RefreshTokenRepository,
    authTokenService: AuthTokenService,
    userId: Long,
    familyId: String,
    issuedAt: kotlin.time.Instant = Clock.System.now(),
    expiredAt: kotlin.time.Instant = issuedAt + 10.minutes,
): RefreshToken {
    val token =
        authTokenService.issueRefreshToken(
            userId = userId,
            familyId = familyId,
        )

    return refreshTokenRepository.insert(
        RefreshToken(
            userId = userId,
            familyId = familyId,
            token = token,
            issuedAt = issuedAt,
            expiredAt = expiredAt,
        ),
    )
}

private fun missingUserIdRefreshToken(securityConfig: SecurityConfig): String =
    JWT
        .create()
        .withClaim("familyId", "family-id")
        .sign(Algorithm.HMAC256(securityConfig.jwtSecret))
        .toRefreshToken()

private fun missingFamilyIdRefreshToken(securityConfig: SecurityConfig): String =
    JWT
        .create()
        .withSubject(RandomFixture.randomId().toString())
        .sign(Algorithm.HMAC256(securityConfig.jwtSecret))
        .toRefreshToken()

private fun issueRefreshTokenAt(
    securityConfig: SecurityConfig,
    userId: Long,
    familyId: String,
    issuedAt: kotlin.time.Instant,
): String =
    JWT
        .create()
        .withSubject(userId.toString())
        .withClaim("familyId", familyId)
        .withIssuedAt(Date.from(issuedAt.toJavaInstant()))
        .sign(Algorithm.HMAC256(securityConfig.jwtSecret))
        .toRefreshToken()

private fun String.toRefreshToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(Charsets.UTF_8))
