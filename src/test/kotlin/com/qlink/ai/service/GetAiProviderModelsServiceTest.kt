package com.qlink.ai.service

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.domain.UserProvider
import com.qlink.ai.domain.UserProviderRole
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class GetAiProviderModelsServiceTest :
    BaseServiceTest({
        val getAiProviderModelsService = koinGet<GetAiProviderModelsService>()
        val userRepository = koinGet<UserRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val userProviderRepository = koinGet<UserProviderRepository>()

        suspend fun randomProvider(excludingTypes: Set<AiProviderType>): AiProvider =
            AiFixture
                .createRandomValidAiProvider(excludingTypes = excludingTypes)
                .let { aiProvider -> aiProviderRepository.findByType(aiProvider.type) ?: aiProviderRepository.insert(aiProvider) }

        suspend fun firstModelOf(provider: AiProvider): AvailableModel =
            availableModelRepository
                .findAllByProviderId(provider.id!!)
                .firstOrNull()
                ?: availableModelRepository.insert(AiFixture.createRandomAvailableModelOf(providerId = provider.id))

        Given("AI Provider 설정 조회 서비스 테스트") {
            lateinit var user: User
            lateinit var superAdminProvider: AiProvider
            lateinit var userProvider: AiProvider
            lateinit var userModel: AvailableModel

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                val superAdmin = userRepository.insert(UserFixture.createRandomValidUser())
                superAdminProvider = randomProvider(excludingTypes = emptySet())
                firstModelOf(superAdminProvider)
                userProvider = randomProvider(excludingTypes = setOf(superAdminProvider.type))
                userModel = firstModelOf(userProvider)
                userProviderRepository.insert(
                    UserProvider(
                        userId = superAdmin.id!!,
                        providerId = superAdminProvider.id!!,
                        userRole = UserProviderRole.SUPER_ADMIN,
                        apiKey = "super-admin-api-key",
                    ),
                )
                userProviderRepository.insert(
                    UserProvider(
                        userId = user.id!!,
                        providerId = userProvider.id!!,
                        userRole = UserProviderRole.NORMAL,
                        apiKey = "user-api-key",
                    ),
                )
            }

            When("JWT 없이 조회하면") {
                val get =
                    suspend {
                        getAiProviderModelsService.getAiProviderModels(loginId = null)
                    }

                Then("SUPER_ADMIN provider의 DEFAULT 모델만 반환한다") {
                    val response = get()
                    val defaultProvider = response.first { it.providerId == superAdminProvider.id }

                    defaultProvider.providerType shouldBe superAdminProvider.type
                    defaultProvider.models.shouldHaveSize(1)
                    defaultProvider.models.first().model shouldBe "DEFAULT"
                }
            }

            When("로그인 사용자가 조회하면") {
                val get =
                    suspend {
                        getAiProviderModelsService.getAiProviderModels(loginId = user.id!!)
                    }

                Then("사용자 provider와 SUPER_ADMIN provider를 함께 반환한다") {
                    val response = get()
                    val ownProvider = response.first { it.providerId == userProvider.id }
                    val defaultProvider = response.first { it.providerId == superAdminProvider.id }

                    ownProvider.models.first().id shouldBe userModel.id
                    ownProvider.models.first().model shouldBe userModel.model
                    defaultProvider.models.first().model shouldBe "DEFAULT"
                }
            }

            When("로그인 사용자가 없으면") {
                val get =
                    suspend {
                        getAiProviderModelsService.getAiProviderModels(loginId = RandomFixture.randomId())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }
    })
