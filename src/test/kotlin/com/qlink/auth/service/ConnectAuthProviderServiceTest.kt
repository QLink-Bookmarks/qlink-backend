package com.qlink.auth.service

import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.dto.AuthPlatform
import com.qlink.auth.dto.ConnectAuthProviderRequest
import com.qlink.auth.repository.AuthProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.FakeAuthResourceClient
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ConnectAuthProviderServiceTest :
    BaseServiceTest({
        val service = koinGet<ConnectAuthProviderService>()
        val userRepository = koinGet<UserRepository>()
        val authProviderRepository = koinGet<AuthProviderRepository>()
        val authResourceClient = koinGet<FakeAuthResourceClient>()

        suspend fun insertUser(): Long =
            requireNotNull(
                userRepository
                    .insert(
                        User(
                            username = "user-${RandomFixture.randomId()}",
                            nickname = RandomFixture.randomSentenceWithMax(50),
                        ),
                    ).id,
            )

        Given("소셜 인증 정보 연동 요청이 주어졌을 때") {
            When("연동되지 않은 kakao provider면") {
                authResourceClient.reset()
                val userId = insertUser()
                val providerId = "kakao-${RandomFixture.randomId()}"
                authResourceClient.providerId = providerId

                val response =
                    service.connect(
                        loginId = userId,
                        request =
                            ConnectAuthProviderRequest(
                                provider = "kakao",
                                token = "oauth-token",
                                platform = AuthPlatform.NATIVE,
                            ),
                    )
                val persisted =
                    authProviderRepository.findByProvider(
                        providerType = AuthProviderType.KAKAO,
                        providerId = providerId,
                    )

                Then("사용자 프로바이더를 생성하고 id를 반환한다") {
                    persisted.shouldNotBeNull()
                    persisted.userId shouldBe userId
                    persisted.providerType shouldBe AuthProviderType.KAKAO
                    response.id shouldBe persisted.id
                }
            }

            When("로그인 사용자가 없으면") {
                authResourceClient.reset()
                authResourceClient.providerId = "kakao-${RandomFixture.randomId()}"
                val connect =
                    suspend {
                        service.connect(
                            loginId = RandomFixture.randomId(),
                            request =
                                ConnectAuthProviderRequest(
                                    provider = "kakao",
                                    token = "oauth-token",
                                    platform = AuthPlatform.NATIVE,
                                ),
                        )
                    }

                Then("USER_NOT_FOUND 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        connect()
                    }
                }
            }

            When("이미 같은 프로바이더가 연동되어 있으면") {
                authResourceClient.reset()
                val userId = insertUser()
                authProviderRepository.insert(
                    AuthProvider(
                        userId = userId,
                        providerType = AuthProviderType.KAKAO,
                        providerId = "kakao-${RandomFixture.randomId()}",
                    ),
                )
                authResourceClient.providerId = "kakao-${RandomFixture.randomId()}"
                val connect =
                    suspend {
                        service.connect(
                            loginId = userId,
                            request =
                                ConnectAuthProviderRequest(
                                    provider = "KAKAO",
                                    token = "oauth-token",
                                    platform = AuthPlatform.NATIVE,
                                ),
                        )
                    }

                Then("AUTH_PROVIDER_ALREADY_CONNECTED 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_ALREADY_CONNECTED.message) {
                        connect()
                    }
                }
            }

            When("요청한 소셜 계정이 이미 다른 사용자에게 연동되어 있으면") {
                authResourceClient.reset()
                val otherUserId = insertUser()
                val userId = insertUser()
                val providerId = "kakao-${RandomFixture.randomId()}"
                authProviderRepository.insert(
                    AuthProvider(
                        userId = otherUserId,
                        providerType = AuthProviderType.KAKAO,
                        providerId = providerId,
                    ),
                )
                authResourceClient.providerId = providerId
                val connect =
                    suspend {
                        service.connect(
                            loginId = userId,
                            request =
                                ConnectAuthProviderRequest(
                                    provider = "KAKAO",
                                    token = "oauth-token",
                                    platform = AuthPlatform.NATIVE,
                                ),
                        )
                    }

                Then("AUTH_PROVIDER_ALREADY_LINKED 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AUTH_PROVIDER_ALREADY_LINKED.message) {
                        connect()
                    }
                }
            }
        }
    })
