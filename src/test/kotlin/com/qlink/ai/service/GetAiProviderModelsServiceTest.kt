package com.qlink.ai.service

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class GetAiProviderModelsServiceTest :
    BaseServiceTest({
        val service = koinGet<GetAiProviderModelsService>()
        val userRepository = koinGet<UserRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val userProviderRepository = koinGet<UserProviderRepository>()

        suspend fun insertProvider(type: AiProviderType): AiProvider =
            aiProviderRepository.findByType(type)
                ?: aiProviderRepository.insert(
                    AiProvider(
                        type = type,
                        baseUrl = RandomFixture.randomUrl(),
                    ),
                )

        suspend fun insertModel(
            providerId: Long,
            model: String = "model-${RandomFixture.randomId()}",
            priority: Int = RandomFixture.randomInt(1, 100),
        ): AvailableModel =
            availableModelRepository.insert(
                AvailableModel(
                    providerId = providerId,
                    model = model,
                    priority = priority,
                    rpdLimit = RandomFixture.randomInt(1, 1000),
                    tpdLimit = RandomFixture.randomInt(1, 10_000_000),
                ),
            )

        suspend fun insertUserProvider(
            user: User,
            provider: AiProvider,
            role: Role,
        ): UserProvider =
            userProviderRepository.insert(
                UserProvider(
                    userId = user.id!!,
                    providerId = provider.id!!,
                    userRole = role,
                    apiKey = "api-key-${RandomFixture.randomId()}",
                ),
            )

        Given("AI Provider 설정 조회 서비스 테스트") {
            lateinit var user: User
            lateinit var superAdmin: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                superAdmin = userRepository.insert(UserFixture.createRandomValidSuperAdmin())
            }

            When("API key를 등록하지 않은 사용자가 조회하면") {
                Then("SUPER_ADMIN 소유 provider별 DEFAULT 모델 1개씩만 반환한다") {
                    val geminiProvider = insertProvider(AiProviderType.GEMINI)
                    val openAiProvider = insertProvider(AiProviderType.OPENAI)
                    val geminiProviderId = geminiProvider.id!!
                    val openAiProviderId = openAiProvider.id!!
                    val geminiDefaultModel = insertModel(providerId = geminiProviderId, model = "gemini-default", priority = 1)
                    insertModel(providerId = geminiProviderId, model = "gemini-sub", priority = 2)
                    val openAiDefaultModel = insertModel(providerId = openAiProviderId, model = "openai-default", priority = 1)
                    insertModel(providerId = openAiProviderId, model = "openai-sub", priority = 2)
                    val geminiSuperAdminProvider =
                        insertUserProvider(
                            user = superAdmin,
                            provider = geminiProvider,
                            role = Role.SUPER_ADMIN,
                        )
                    val openAiSuperAdminProvider =
                        insertUserProvider(
                            user = superAdmin,
                            provider = openAiProvider,
                            role = Role.SUPER_ADMIN,
                        )

                    val response = service.getAiProviderModels(loginId = user.id!!)
                    val geminiResponse = response.first { it.providerId == geminiProviderId }
                    val openAiResponse = response.first { it.providerId == openAiProviderId }

                    response shouldHaveSize 2
                    geminiResponse.models shouldHaveSize 1
                    geminiResponse.models.first().id shouldBe geminiDefaultModel.id
                    geminiResponse.models.first().model shouldBe "DEFAULT"
                    geminiResponse.models.first().userProviderId shouldBe geminiSuperAdminProvider.id
                    openAiResponse.models shouldHaveSize 1
                    openAiResponse.models.first().id shouldBe openAiDefaultModel.id
                    openAiResponse.models.first().model shouldBe "DEFAULT"
                    openAiResponse.models.first().userProviderId shouldBe openAiSuperAdminProvider.id
                }
            }

            When("Gemini API key를 등록한 사용자가 조회하면") {
                Then("Gemini는 사용자 provider의 모든 모델을, 나머지는 SUPER_ADMIN DEFAULT 모델만 반환한다") {
                    val geminiProvider = insertProvider(AiProviderType.GEMINI)
                    val openAiProvider = insertProvider(AiProviderType.OPENAI)
                    val geminiProviderId = geminiProvider.id!!
                    val openAiProviderId = openAiProvider.id!!
                    val geminiFirstModel = insertModel(providerId = geminiProviderId, model = "gemini-first", priority = 1)
                    val geminiSecondModel = insertModel(providerId = geminiProviderId, model = "gemini-second", priority = 2)
                    val openAiDefaultModel = insertModel(providerId = openAiProviderId, model = "openai-default", priority = 1)
                    insertModel(providerId = openAiProviderId, model = "openai-sub", priority = 2)
                    insertUserProvider(
                        user = superAdmin,
                        provider = geminiProvider,
                        role = Role.SUPER_ADMIN,
                    )
                    val openAiSuperAdminProvider =
                        insertUserProvider(
                            user = superAdmin,
                            provider = openAiProvider,
                            role = Role.SUPER_ADMIN,
                        )
                    val geminiUserProvider =
                        insertUserProvider(
                            user = user,
                            provider = geminiProvider,
                            role = Role.NORMAL,
                        )

                    val response = service.getAiProviderModels(loginId = user.id!!)
                    val geminiResponse = response.first { it.providerId == geminiProviderId }
                    val openAiResponse = response.first { it.providerId == openAiProviderId }

                    response shouldHaveSize 2
                    geminiResponse.models.map { it.id } shouldContainExactly listOf(geminiFirstModel.id, geminiSecondModel.id)
                    geminiResponse.models.map { it.model } shouldContainExactly listOf(geminiFirstModel.model, geminiSecondModel.model)
                    geminiResponse.models.map { it.userProviderId }.distinct() shouldContainExactly listOf(geminiUserProvider.id)
                    openAiResponse.models shouldHaveSize 1
                    openAiResponse.models.first().id shouldBe openAiDefaultModel.id
                    openAiResponse.models.first().model shouldBe "DEFAULT"
                    openAiResponse.models.first().userProviderId shouldBe openAiSuperAdminProvider.id
                }
            }

            When("로그인 사용자가 없으면") {
                val get =
                    suspend {
                        service.getAiProviderModels(loginId = RandomFixture.randomId())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }
    })
