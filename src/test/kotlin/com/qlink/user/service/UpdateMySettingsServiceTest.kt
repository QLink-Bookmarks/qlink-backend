package com.qlink.user.service

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AiProviderType
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.AiFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.domain.UserAccent
import com.qlink.user.domain.UserTheme
import com.qlink.user.dto.UpdateMySettingsRequest
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe

class UpdateMySettingsServiceTest :
    BaseServiceTest({
        val updateMySettingsService = koinGet<UpdateMySettingsService>()
        val userRepository = koinGet<UserRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()

        Given("내 설정 변경 서비스 테스트") {
            lateinit var provider: AiProvider
            lateinit var anotherProvider: AiProvider
            lateinit var model: AvailableModel
            lateinit var anotherModel: AvailableModel
            lateinit var user: User

            beforeTest {
                provider = providerFixture(type = AiProviderType.GEMINI, aiProviderRepository = aiProviderRepository)
                anotherProvider = providerFixture(type = AiProviderType.OPENAI, aiProviderRepository = aiProviderRepository)
                model = modelFixture(providerId = provider.id!!, availableModelRepository = availableModelRepository)
                anotherModel = modelFixture(providerId = anotherProvider.id!!, availableModelRepository = availableModelRepository)
                user =
                    userRepository.insert(
                        UserFixture
                            .createRandomValidUser()
                            .copyForSettings(
                                theme = UserTheme.LIGHT,
                                accent = UserAccent.GRAY,
                                allowsReminder = true,
                                defaultAiProviderId = provider.id,
                                defaultModelId = model.id,
                            ),
                    )
            }

            When("일부 설정만 변경하면") {
                val update =
                    suspend {
                        updateMySettingsService.updateMySettings(
                            loginId = user.id!!,
                            request =
                                UpdateMySettingsRequest(
                                    theme = UserTheme.DARK.responseName,
                                    allowsReminder = false,
                                    defaultModelId = anotherModel.id,
                                    defaultProviderId = anotherProvider.id,
                                ),
                        )
                    }

                Then("요청한 설정만 변경하고 나머지는 유지한다") {
                    shouldNotThrow<BusinessException> {
                        update()
                    }

                    val updated = userRepository.findById(user.id!!)!!

                    updated.theme shouldBe UserTheme.DARK
                    updated.accent shouldBe UserAccent.GRAY
                    updated.allowsReminder shouldBe false
                    updated.defaultAiProviderId shouldBe anotherProvider.id
                    updated.defaultModelId shouldBe anotherModel.id
                }
            }

            When("기존 설정과 같은 값으로 요청하면") {
                val update =
                    suspend {
                        updateMySettingsService.updateMySettings(
                            loginId = user.id!!,
                            request =
                                UpdateMySettingsRequest(
                                    theme = user.theme.responseName,
                                    accent = user.accent.responseName,
                                    allowsReminder = user.allowsReminder,
                                    defaultProviderId = user.defaultAiProviderId,
                                    defaultModelId = user.defaultModelId,
                                ),
                        )
                    }

                Then("저장소 update 없이 성공한다") {
                    shouldNotThrow<BusinessException> {
                        update()
                    }

                    val updated = userRepository.findById(user.id!!)!!

                    updated.updatedAt shouldBe user.updatedAt
                }
            }

            When("전체 설정이 null이면") {
                val update =
                    suspend {
                        updateMySettingsService.updateMySettings(
                            loginId = user.id!!,
                            request = UpdateMySettingsRequest(),
                        )
                    }

                Then("변경 없이 성공한다") {
                    shouldNotThrow<BusinessException> {
                        update()
                    }

                    val updated = userRepository.findById(user.id!!)!!

                    updated.theme shouldBe user.theme
                    updated.accent shouldBe user.accent
                    updated.allowsReminder shouldBe user.allowsReminder
                    updated.defaultAiProviderId shouldBe user.defaultAiProviderId
                    updated.defaultModelId shouldBe user.defaultModelId
                    updated.updatedAt shouldBe user.updatedAt
                }
            }

            When("provider만 변경했지만 기존 model이 provider에 속하지 않으면") {
                val update =
                    suspend {
                        updateMySettingsService.updateMySettings(
                            loginId = user.id!!,
                            request =
                                UpdateMySettingsRequest(
                                    defaultProviderId = anotherProvider.id,
                                ),
                        )
                    }

                Then("모델 소속 불일치 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_MODEL_DIFFERENT_PROVIDER.message) {
                        update()
                    }
                }
            }

            When("로그인 사용자가 없으면") {
                val update =
                    suspend {
                        updateMySettingsService.updateMySettings(
                            loginId = RandomFixture.randomId(),
                            request =
                                UpdateMySettingsRequest(
                                    theme = UserTheme.DARK.responseName,
                                ),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        update()
                    }
                }
            }
        }
    })

private suspend fun providerFixture(
    type: AiProviderType,
    aiProviderRepository: AiProviderRepository,
): AiProvider {
    val provider = AiFixture.createRandomValidAiProvider(type = type)
    return aiProviderRepository.findByType(type) ?: aiProviderRepository.insert(provider)
}

private suspend fun modelFixture(
    providerId: Long,
    availableModelRepository: AvailableModelRepository,
): AvailableModel =
    availableModelRepository.findAllByProviderId(providerId).firstOrNull()
        ?: availableModelRepository.insert(AiFixture.createRandomAvailableModelOf(providerId = providerId))

private fun User.copyForSettings(
    theme: UserTheme,
    accent: UserAccent,
    allowsReminder: Boolean,
    defaultAiProviderId: Long?,
    defaultModelId: Long?,
): User =
    User(
        id = id,
        username = username,
        nickname = nickname,
        role = role,
        avatarUrl = avatarUrl,
        avatarEmoji = avatarEmoji,
        theme = theme,
        accent = accent,
        allowsReminder = allowsReminder,
        defaultAiProviderId = defaultAiProviderId,
        defaultModelId = defaultModelId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
