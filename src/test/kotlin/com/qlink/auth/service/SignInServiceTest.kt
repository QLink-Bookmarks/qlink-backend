package com.qlink.auth.service

import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform
import com.qlink.auth.dto.SignInRequest
import com.qlink.auth.repository.AuthProviderRepository
import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.AppleTestKeys
import com.qlink.support.BaseServiceTest
import com.qlink.support.FakeAuthResourceClient
import com.qlink.support.GoogleTestKeys
import com.qlink.support.MockAuthHttpEngine
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class SignInServiceTest :
    BaseServiceTest({
        val service = koinGet<SignInService>()
        val userRepository = koinGet<UserRepository>()
        val authProviderRepository = koinGet<AuthProviderRepository>()
        val refreshTokenRepository = koinGet<RefreshTokenRepository>()
        val authTokenService = koinGet<AuthTokenService>()
        val authResourceClient = koinGet<FakeAuthResourceClient>()
        val mockAuthHttpEngine = koinGet<MockAuthHttpEngine>()

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
                authResourceClient.failure = BusinessException(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID)
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
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        signIn()
                    }
                }
            }

            When("등록되지 않은 google WEB provider user면") {
                mockAuthHttpEngine.reset()
                val providerId = "google-${RandomFixture.randomId()}"
                mockAuthHttpEngine.respondJson("""{"sub":"$providerId","email":"user@example.com"}""")

                val response =
                    service.signIn(
                        SignInRequest(
                            provider = "google",
                            token = "google-access-token",
                            platform = AuthPlatform.WEB,
                        ),
                    )
                val refreshTokenClaims = authTokenService.verifyRefreshToken(response.refreshToken)
                val authProvider =
                    authProviderRepository.findByProvider(
                        providerType = AuthProviderType.GOOGLE,
                        providerId = providerId,
                    )
                val user = userRepository.findById(refreshTokenClaims.userId)

                Then("access token userinfo의 sub로 회원가입 후 token을 발급한다") {
                    response.accessToken.shouldNotBeNull()
                    authProvider.shouldNotBeNull()
                    authProvider.providerType shouldBe AuthProviderType.GOOGLE
                    authProvider.userId shouldBe refreshTokenClaims.userId
                    user.shouldNotBeNull()
                }
            }

            When("등록되지 않은 google NATIVE provider user면") {
                mockAuthHttpEngine.reset()
                mockAuthHttpEngine.respondJson(GoogleTestKeys.jwks())
                val providerId = "google-native-${RandomFixture.randomId()}"
                val idToken = GoogleTestKeys.idToken(subject = providerId)

                val response =
                    service.signIn(
                        SignInRequest(
                            provider = "google",
                            token = idToken,
                            platform = AuthPlatform.NATIVE,
                        ),
                    )
                val refreshTokenClaims = authTokenService.verifyRefreshToken(response.refreshToken)
                val authProvider =
                    authProviderRepository.findByProvider(
                        providerType = AuthProviderType.GOOGLE,
                        providerId = providerId,
                    )
                val user = userRepository.findById(refreshTokenClaims.userId)

                Then("id_token의 sub로 회원가입 후 token을 발급한다") {
                    response.accessToken.shouldNotBeNull()
                    authProvider.shouldNotBeNull()
                    authProvider.providerType shouldBe AuthProviderType.GOOGLE
                    authProvider.userId shouldBe refreshTokenClaims.userId
                    user.shouldNotBeNull()
                }
            }

            When("등록되지 않은 naver provider user면") {
                mockAuthHttpEngine.reset()
                val providerId = "naver-${RandomFixture.randomId()}"
                mockAuthHttpEngine.respondJson(
                    """{"resultcode":"00","message":"success","response":{"id":"$providerId"}}""",
                )

                val response =
                    service.signIn(
                        SignInRequest(
                            provider = "naver",
                            token = "naver-access-token",
                            platform = AuthPlatform.NATIVE,
                        ),
                    )
                val refreshTokenClaims = authTokenService.verifyRefreshToken(response.refreshToken)
                val authProvider =
                    authProviderRepository.findByProvider(
                        providerType = AuthProviderType.NAVER,
                        providerId = providerId,
                    )
                val user = userRepository.findById(refreshTokenClaims.userId)

                Then("naver userinfo의 id로 회원가입 후 token을 발급한다") {
                    response.accessToken.shouldNotBeNull()
                    authProvider.shouldNotBeNull()
                    authProvider.providerType shouldBe AuthProviderType.NAVER
                    authProvider.userId shouldBe refreshTokenClaims.userId
                    user.shouldNotBeNull()
                }
            }

            When("google WEB userinfo 요청이 실패 응답을 주면") {
                mockAuthHttpEngine.reset()
                mockAuthHttpEngine.respondJson("""{"error":"invalid_token"}""", HttpStatusCode.Unauthorized)
                val signIn =
                    suspend {
                        service.signIn(
                            SignInRequest(
                                provider = "google",
                                token = "invalid-google-token",
                                platform = AuthPlatform.WEB,
                            ),
                        )
                    }

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        signIn()
                    }
                }
            }

            When("등록되지 않은 apple provider user면") {
                mockAuthHttpEngine.reset()
                mockAuthHttpEngine.respondJson(AppleTestKeys.jwks())
                val providerId = "apple-${RandomFixture.randomId()}"
                val idToken = AppleTestKeys.idToken(subject = providerId)

                val response =
                    service.signIn(
                        SignInRequest(
                            provider = "apple",
                            token = idToken,
                            platform = AuthPlatform.NATIVE,
                        ),
                    )
                val refreshTokenClaims = authTokenService.verifyRefreshToken(response.refreshToken)
                val authProvider =
                    authProviderRepository.findByProvider(
                        providerType = AuthProviderType.APPLE,
                        providerId = providerId,
                    )
                val user = userRepository.findById(refreshTokenClaims.userId)

                Then("id_token의 sub로 회원가입 후 token을 발급한다") {
                    response.accessToken.shouldNotBeNull()
                    authProvider.shouldNotBeNull()
                    authProvider.providerType shouldBe AuthProviderType.APPLE
                    authProvider.userId shouldBe refreshTokenClaims.userId
                    user.shouldNotBeNull()
                }
            }

            When("apple id_token 검증에 실패하면") {
                mockAuthHttpEngine.reset()
                mockAuthHttpEngine.respondJson(AppleTestKeys.jwks())
                val idToken = AppleTestKeys.idToken(subject = "u", signWithWrongKey = true)
                val signIn =
                    suspend {
                        service.signIn(
                            SignInRequest(
                                provider = "apple",
                                token = idToken,
                                platform = AuthPlatform.NATIVE,
                            ),
                        )
                    }

                Then("외부 client 실패 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_TOKEN_INVALID.message) {
                        signIn()
                    }
                }
            }
        }
    })
