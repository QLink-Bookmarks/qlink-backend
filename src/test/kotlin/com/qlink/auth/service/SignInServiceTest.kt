package com.qlink.auth.service

import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform
import com.qlink.auth.dto.SignInRequest
import com.qlink.auth.repository.AuthProviderRepository
import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.FakeAuthResourceClient
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SignInServiceTest :
    BaseServiceTest({
        val service = koinGet<SignInService>()
        val userRepository = koinGet<UserRepository>()
        val authProviderRepository = koinGet<AuthProviderRepository>()
        val refreshTokenRepository = koinGet<RefreshTokenRepository>()
        val authTokenService = koinGet<AuthTokenService>()
        val authResourceClient = koinGet<FakeAuthResourceClient>()

        Given("인증 API 요청이 주어졌을 때") {
            When("등록되지 않은 kakao provider user면") {
                authResourceClient.reset()
                val providerId = "kakao-${RandomFixture.randomId()}"
                authResourceClient.providerId = providerId
                val response =
                    service.signIn(
                        SignInRequest(
                            provider = "kakao",
                            token = "oauth-token",
                            platform = AuthPlatform.NATIVE,
                        ),
                    )
                val refreshTokenClaims = authTokenService.verifyRefreshToken(response.refreshToken)
                val authProvider =
                    authProviderRepository.findByProvider(
                        providerType = AuthProviderType.KAKAO,
                        providerId = providerId,
                    )
                val user = userRepository.findById(refreshTokenClaims.userId)
                val persistedRefreshToken = refreshTokenRepository.findByToken(response.refreshToken)

                Then("회원가입 후 token을 발급하고 저장한다") {
                    authResourceClient.requestedTokens shouldContain "oauth-token"
                    response.accessToken.shouldNotBeNull()
                    authProvider.shouldNotBeNull()
                    authProvider.userId shouldBe refreshTokenClaims.userId
                    user.shouldNotBeNull()
                    persistedRefreshToken.shouldNotBeNull()
                    persistedRefreshToken.userId shouldBe refreshTokenClaims.userId
                    persistedRefreshToken.familyId shouldBe refreshTokenClaims.familyId
                }
            }

            When("이미 등록된 kakao provider user면") {
                authResourceClient.reset()
                val user =
                    userRepository.insert(
                        User(
                            username = "user-${RandomFixture.randomId()}",
                            nickname = RandomFixture.randomSentenceWithMax(50),
                        ),
                    )
                val userId = requireNotNull(user.id)
                val providerId = "kakao-${RandomFixture.randomId()}"
                authProviderRepository.insert(
                    AuthProvider(
                        userId = userId,
                        providerType = AuthProviderType.KAKAO,
                        providerId = providerId,
                    ),
                )
                authResourceClient.providerId = providerId

                val response =
                    service.signIn(
                        SignInRequest(
                            provider = "KAKAO",
                            token = "oauth-token",
                            platform = AuthPlatform.NATIVE,
                        ),
                    )
                val refreshTokenClaims = authTokenService.verifyRefreshToken(response.refreshToken)
                val persistedRefreshToken = refreshTokenRepository.findByToken(response.refreshToken)

                Then("기존 사용자로 로그인하고 token을 저장한다") {
                    refreshTokenClaims.userId shouldBe userId
                    persistedRefreshToken.shouldNotBeNull()
                    persistedRefreshToken.userId shouldBe userId
                }
            }

            When("외부 인증 client가 실패하면") {
                authResourceClient.reset()
                authResourceClient.failure = BusinessException(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED)
                val signIn =
                    suspend {
                        service.signIn(
                            SignInRequest(
                                provider = "kakao",
                                token = "invalid-oauth-token",
                                platform = AuthPlatform.NATIVE,
                            ),
                        )
                    }

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_EXTERNAL_CLIENT_FAILED.message) {
                        signIn()
                    }
                }
            }
        }
    })
