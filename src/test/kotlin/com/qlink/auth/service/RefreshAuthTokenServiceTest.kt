package com.qlink.auth.service

import com.qlink.auth.domain.RefreshToken
import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.SecurityConfig
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.fixture.UserRefreshTokenFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RefreshAuthTokenServiceTest :
    BaseServiceTest({
        val service = koinGet<RefreshAuthTokenService>()
        val userRepository = koinGet<UserRepository>()
        val refreshTokenRepository = koinGet<RefreshTokenRepository>()
        val authTokenService = koinGet<AuthTokenService>()
        val securityConfig = koinGet<SecurityConfig>()

        Given("토큰 갱신 요청이 주어졌을 때") {
            lateinit var user: User

            beforeTest {
                user = insertUser(userRepository)
            }

            When("저장된 refresh token이면") {
                lateinit var currentToken: RefreshToken
                lateinit var familyId: String

                beforeTest {
                    val userId = requireNotNull(user.id)
                    familyId = "family-${RandomFixture.randomId()}"
                    val issuedAt = Clock.System.now() - 2.minutes
                    currentToken =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = userId,
                            familyId = familyId,
                            issuedAt = issuedAt,
                            expiredAt = Clock.System.now() + 10.minutes,
                            token =
                                UserRefreshTokenFixture.issueSignedTokenAt(
                                    securityConfig = securityConfig,
                                    userId = userId,
                                    familyId = familyId,
                                    issuedAt = issuedAt,
                                ),
                        )
                }

                val refresh = suspend { service.refresh(currentToken.token) }

                Then("새 refresh token으로 회전하고 저장한다") {
                    val response = refresh()
                    val claims = authTokenService.verifyRefreshToken(response.refreshToken)
                    val persistedRefreshToken = refreshTokenRepository.findByToken(response.refreshToken)

                    claims.userId shouldBe user.id
                    claims.familyId shouldBe familyId
                    persistedRefreshToken.shouldNotBeNull()
                    persistedRefreshToken.userId shouldBe user.id
                    persistedRefreshToken.id shouldBe currentToken.id
                    persistedRefreshToken.expiredAt - persistedRefreshToken.issuedAt shouldBe 14.days
                    refreshTokenRepository.findByToken(currentToken.token).shouldBeNull()
                }
            }

            When("동시 갱신으로 기존 token이 이미 회전됐고 최신 token이 1분 이내면") {
                lateinit var requestedToken: String
                lateinit var latestToken: RefreshToken

                beforeTest {
                    val userId = requireNotNull(user.id)
                    val familyId = "family-${RandomFixture.randomId()}"
                    requestedToken =
                        UserRefreshTokenFixture.issueSignedTokenAt(
                            securityConfig = securityConfig,
                            userId = userId,
                            familyId = familyId,
                            issuedAt = Clock.System.now() - 1.minutes,
                        )
                    latestToken =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = userId,
                            familyId = familyId,
                        )
                }

                val refresh = suspend { service.refresh(requestedToken) }

                Then("저장된 최신 refresh token을 응답한다") {
                    val firstResponse = refresh()
                    val firstPersisted = refreshTokenRepository.findByToken(latestToken.token)
                    val secondResponse = refresh()

                    firstResponse.refreshToken shouldBe latestToken.token
                    secondResponse.refreshToken shouldBe latestToken.token
                    refreshTokenRepository.findByToken(latestToken.token) shouldBe firstPersisted
                }
            }

            When("요청 refresh token이 공백이면") {
                lateinit var persisted: RefreshToken

                beforeTest {
                    persisted =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = requireNotNull(user.id),
                            familyId = "family-${RandomFixture.randomId()}",
                        )
                }

                val refresh =
                    suspend {
                        service.refresh("   ")
                    }

                Then("인증 정보 없음 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_REFRESH_TOKEN_MISSING.message) {
                        refresh()
                    }
                    refreshTokenRepository.findByToken(persisted.token) shouldBe persisted
                }
            }

            When("refresh token jwt의 사용자가 DB에 없으면") {
                val token =
                    authTokenService.issueRefreshToken(
                        userId = RandomFixture.randomId(),
                        familyId = "family-${RandomFixture.randomId()}",
                    )
                val refresh =
                    suspend {
                        service.refresh(token)
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_REFRESH_TOKEN_INVALID.message) {
                        refresh()
                    }
                    refreshTokenRepository.findByToken(token).shouldBeNull()
                }
            }

            When("저장된 refresh token이 없으면") {
                lateinit var refreshToken: String
                lateinit var otherFamilyToken: RefreshToken

                beforeTest {
                    val userId = requireNotNull(user.id)
                    refreshToken =
                        authTokenService.issueRefreshToken(
                            userId = userId,
                            familyId = "family-${RandomFixture.randomId()}",
                        )
                    otherFamilyToken =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = userId,
                            familyId = "family-${RandomFixture.randomId()}",
                        )
                }

                val refresh =
                    suspend {
                        service.refresh(refreshToken)
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_REFRESH_TOKEN_REUSED.message) {
                        refresh()
                    }
                    refreshTokenRepository.findByToken(otherFamilyToken.token) shouldBe otherFamilyToken
                }
            }

            When("저장된 최신 refresh token이 1분을 지났으면") {
                lateinit var familyId: String
                lateinit var requestedToken: String
                lateinit var older: RefreshToken
                lateinit var latest: RefreshToken
                lateinit var otherFamily: RefreshToken

                beforeTest {
                    val userId = requireNotNull(user.id)
                    familyId = "family-${RandomFixture.randomId()}"
                    requestedToken =
                        UserRefreshTokenFixture.issueSignedTokenAt(
                            securityConfig = securityConfig,
                            userId = userId,
                            familyId = familyId,
                            issuedAt = Clock.System.now() - 3.minutes,
                        )
                    val olderIssuedAt = Clock.System.now() - 4.minutes
                    older =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = userId,
                            familyId = familyId,
                            issuedAt = olderIssuedAt,
                            token =
                                UserRefreshTokenFixture.issueSignedTokenAt(
                                    securityConfig = securityConfig,
                                    userId = userId,
                                    familyId = familyId,
                                    issuedAt = olderIssuedAt,
                                ),
                        )
                    val latestIssuedAt = Clock.System.now() - 2.minutes
                    latest =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = userId,
                            familyId = familyId,
                            issuedAt = latestIssuedAt,
                            token =
                                UserRefreshTokenFixture.issueSignedTokenAt(
                                    securityConfig = securityConfig,
                                    userId = userId,
                                    familyId = familyId,
                                    issuedAt = latestIssuedAt,
                                ),
                        )
                    otherFamily =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = userId,
                            familyId = "family-${RandomFixture.randomId()}",
                        )
                }

                val refresh =
                    suspend {
                        service.refresh(requestedToken)
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_REFRESH_TOKEN_REUSED.message) {
                        refresh()
                    }
                    refreshTokenRepository.findByToken(older.token).shouldBeNull()
                    refreshTokenRepository.findByToken(latest.token).shouldBeNull()
                    refreshTokenRepository
                        .findLatestByUserIdAndFamilyId(userId = requireNotNull(user.id), familyId = familyId)
                        .shouldBeNull()
                    refreshTokenRepository.findByToken(otherFamily.token) shouldBe otherFamily
                }
            }

            When("저장된 최신 refresh token이 만료됐으면") {
                lateinit var familyId: String
                lateinit var requestedToken: String
                lateinit var latest: RefreshToken

                beforeTest {
                    val userId = requireNotNull(user.id)
                    familyId = "family-${RandomFixture.randomId()}"
                    requestedToken =
                        UserRefreshTokenFixture.issueSignedTokenAt(
                            securityConfig = securityConfig,
                            userId = userId,
                            familyId = familyId,
                            issuedAt = Clock.System.now() - 1.minutes,
                        )
                    latest =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = userId,
                            familyId = familyId,
                            issuedAt = Clock.System.now(),
                            expiredAt = Clock.System.now() - 1.minutes,
                        )
                }

                val refresh =
                    suspend {
                        service.refresh(requestedToken)
                    }

                Then("인증 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_REFRESH_TOKEN_REUSED.message) {
                        refresh()
                    }
                    refreshTokenRepository.findByToken(latest.token).shouldBeNull()
                    refreshTokenRepository
                        .findLatestByUserIdAndFamilyId(userId = requireNotNull(user.id), familyId = familyId)
                        .shouldBeNull()
                }
            }

            When("저장된 현재 refresh token이 만료됐으면") {
                lateinit var expired: RefreshToken

                beforeTest {
                    expired =
                        insertRefreshToken(
                            refreshTokenRepository = refreshTokenRepository,
                            authTokenService = authTokenService,
                            userId = requireNotNull(user.id),
                            familyId = "family-${RandomFixture.randomId()}",
                            expiredAt = Clock.System.now() - 1.seconds,
                        )
                }

                val refresh =
                    suspend {
                        service.refresh(expired.token)
                    }

                Then("INVALID을 반환하고 조회된 행을 삭제한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_REFRESH_TOKEN_INVALID.message) {
                        refresh()
                    }
                    refreshTokenRepository.findByToken(expired.token).shouldBeNull()
                }
            }
        }
    })

private suspend fun insertUser(userRepository: UserRepository): User = userRepository.insert(UserFixture.createRandomValidUser())

private suspend fun insertRefreshToken(
    refreshTokenRepository: RefreshTokenRepository,
    authTokenService: AuthTokenService,
    userId: Long,
    familyId: String,
    issuedAt: kotlin.time.Instant = Clock.System.now(),
    expiredAt: kotlin.time.Instant = issuedAt + 10.minutes,
    token: String =
        authTokenService.issueRefreshToken(
            userId = userId,
            familyId = familyId,
        ),
): RefreshToken =
    refreshTokenRepository.insert(
        UserRefreshTokenFixture.createForUser(
            userId = userId,
            familyId = familyId,
            token = token,
            issuedAt = issuedAt,
            expiredAt = expiredAt,
        ),
    )
