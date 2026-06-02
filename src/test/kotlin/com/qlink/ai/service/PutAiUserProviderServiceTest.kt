package com.qlink.ai.service

import com.qlink.ai.crypto.AiApiKeyCipher
import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.dto.PutAiUserProviderRequest
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.FakeAiClient
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

class PutAiUserProviderServiceTest :
    BaseServiceTest({
        val service = koinGet<PutAiUserProviderService>()
        val userRepository = koinGet<UserRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val userProviderRepository = koinGet<UserProviderRepository>()
        val apiKeyCipher = koinGet<AiApiKeyCipher>()
        val fakeAiClient = koinGet<FakeAiClient>()

        suspend fun insertProvider(type: AiProviderType = AiProviderType.OPENAI): AiProvider =
            aiProviderRepository.findByType(type)
                ?: aiProviderRepository.insert(
                    AiProvider(
                        type = type,
                        baseUrl = RandomFixture.randomUrl(),
                    ),
                )

        Given("AI API Key 등록 서비스 테스트") {
            lateinit var user: User
            lateinit var provider: AiProvider

            beforeTest {
                fakeAiClient.reset()
                user = userRepository.insert(UserFixture.createRandomValidUser())
                provider = insertProvider()
            }

            When("사용자가 신규 API key를 등록하면") {
                Then("암호문으로 저장하고 등록 ID를 반환한다") {
                    val request =
                        PutAiUserProviderRequest(
                            providerId = provider.id!!,
                            apiKey = "sk-test-${RandomFixture.randomId()}",
                        )

                    val response = service.putAiUserProvider(user.id!!, request)

                    val actual = userProviderRepository.findByUserIdAndProviderId(user.id!!, provider.id!!)!!
                    response.id shouldBe actual.id
                    actual.apiKey shouldStartWith "v1:"
                    actual.apiKey shouldNotBe request.apiKey
                    apiKeyCipher.decrypt(actual.apiKey) shouldBe request.apiKey
                    fakeAiClient.requestedApiKeys shouldBe listOf(request.apiKey)
                }
            }

            When("이미 등록된 provider에 다시 등록하면") {
                Then("같은 user provider를 새 암호문으로 갱신한다") {
                    val firstRequest =
                        PutAiUserProviderRequest(
                            providerId = provider.id!!,
                            apiKey = "first-api-key",
                        )
                    val secondRequest =
                        PutAiUserProviderRequest(
                            providerId = provider.id!!,
                            apiKey = "second-api-key",
                        )

                    val firstResponse = service.putAiUserProvider(user.id!!, firstRequest)
                    val secondResponse = service.putAiUserProvider(user.id!!, secondRequest)

                    val actual = userProviderRepository.findByUserIdAndProviderId(user.id!!, provider.id!!)!!
                    firstResponse.id shouldBe secondResponse.id
                    secondResponse.id shouldBe actual.id
                    apiKeyCipher.decrypt(actual.apiKey) shouldBe secondRequest.apiKey
                    fakeAiClient.requestedApiKeys shouldBe listOf(firstRequest.apiKey, secondRequest.apiKey)
                }
            }

            When("로그인 사용자가 없으면") {
                val register =
                    suspend {
                        service.putAiUserProvider(
                            loginId = RandomFixture.randomId(),
                            request =
                                PutAiUserProviderRequest(
                                    providerId = provider.id!!,
                                    apiKey = "api-key",
                                ),
                        )
                    }

                Then("USER_NOT_FOUND 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        register()
                    }
                }
            }

            When("provider가 없으면") {
                val register =
                    suspend {
                        service.putAiUserProvider(
                            loginId = user.id!!,
                            request =
                                PutAiUserProviderRequest(
                                    providerId = RandomFixture.randomId(),
                                    apiKey = "api-key",
                                ),
                        )
                    }

                Then("AI_PROVIDER_NOT_FOUND 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_PROVIDER_NOT_FOUND.message) {
                        register()
                    }
                }
            }

            When("provider가 지원되지 않으면") {
                Then("AI_PROVIDER_NOT_SUPPORTED 예외를 반환한다") {
                    val unsupportedProvider = insertProvider(AiProviderType.CLAUDE)
                    val register =
                        suspend {
                            service.putAiUserProvider(
                                loginId = user.id!!,
                                request =
                                    PutAiUserProviderRequest(
                                        providerId = unsupportedProvider.id!!,
                                        apiKey = "api-key",
                                    ),
                            )
                        }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_PROVIDER_NOT_SUPPORTED.message) {
                        register()
                    }
                }
            }

            When("vendor가 인증 실패를 반환하면") {
                listOf(401, 403).forEach { statusCode ->
                    Then("$statusCode 상태를 AI_API_KEY_INVALID 예외로 반환한다") {
                        fakeAiClient.validationStatusCode = statusCode
                        val register =
                            suspend {
                                service.putAiUserProvider(
                                    loginId = user.id!!,
                                    request =
                                        PutAiUserProviderRequest(
                                            providerId = provider.id!!,
                                            apiKey = "invalid-api-key",
                                        ),
                                )
                            }

                        shouldThrowWithMessage<BusinessException>(ErrorCode.AI_API_KEY_INVALID.message) {
                            register()
                        }
                    }
                }
            }

            When("vendor가 일시적 오류를 반환하면") {
                Then("AI_VENDOR_TEMPORARY_UNAVAILABLE 예외를 반환한다") {
                    fakeAiClient.validationStatusCode = 503
                    val register =
                        suspend {
                            service.putAiUserProvider(
                                loginId = user.id!!,
                                request =
                                    PutAiUserProviderRequest(
                                        providerId = provider.id!!,
                                        apiKey = "api-key",
                                    ),
                            )
                        }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_VENDOR_TEMPORARY_UNAVAILABLE.message) {
                        register()
                    }
                }
            }

            When("vendor가 정의되지 않은 상태를 반환하면") {
                Then("COMMON_INTERNAL_SERVER_ERROR 예외를 반환한다") {
                    fakeAiClient.validationStatusCode = 418
                    val register =
                        suspend {
                            service.putAiUserProvider(
                                loginId = user.id!!,
                                request =
                                    PutAiUserProviderRequest(
                                        providerId = provider.id!!,
                                        apiKey = "api-key",
                                    ),
                            )
                        }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INTERNAL_SERVER_ERROR.message) {
                        register()
                    }
                }
            }
        }
    })
