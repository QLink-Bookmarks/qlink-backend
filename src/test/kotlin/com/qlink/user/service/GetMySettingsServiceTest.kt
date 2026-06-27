package com.qlink.user.service

import com.qlink.ai.domain.AiProvider
import com.qlink.ai.domain.AvailableModel
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.auth.domain.AuthProvider
import com.qlink.auth.domain.AuthProviderType
import com.qlink.auth.repository.AuthProviderRepository
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
import com.qlink.user.dto.UserAuthProviderResponse
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class GetMySettingsServiceTest :
    BaseServiceTest({
        val getMySettingsService = koinGet<GetMySettingsService>()
        val userRepository = koinGet<UserRepository>()
        val aiProviderRepository = koinGet<AiProviderRepository>()
        val availableModelRepository = koinGet<AvailableModelRepository>()
        val authProviderRepository = koinGet<AuthProviderRepository>()

        Given("내 설정 조회 서비스 테스트") {
            lateinit var provider: AiProvider
            lateinit var model: AvailableModel
            lateinit var user: User

            beforeTest {
                val randomProvider = AiFixture.createRandomValidAiProvider()
                provider = aiProviderRepository.findByType(randomProvider.type)
                    ?: aiProviderRepository.insert(randomProvider)
                model =
                    availableModelRepository
                        .findAllByProviderId(provider.id!!)
                        .firstOrNull()
                        ?: availableModelRepository.insert(
                            AiFixture.createRandomAvailableModelOf(providerId = provider.id!!),
                        )
                user =
                    userRepository.insert(
                        UserFixture
                            .createRandomValidUser()
                            .copyForSettings(
                                theme = UserTheme.DARK,
                                accent = UserAccent.PINK,
                                allowsReminder = false,
                                defaultAiProviderId = provider.id,
                                defaultModelId = model.id,
                            ),
                    )
            }

            When("현재 로그인 사용자의 설정을 조회하면") {
                val get =
                    suspend {
                        getMySettingsService.getMySettings(loginId = user.id!!)
                    }

                Then("화면, 동작, AI 기본 설정을 반환한다") {
                    val response = get()

                    response.display.theme shouldBe UserTheme.DARK.responseName
                    response.display.accent shouldBe UserAccent.PINK.responseName
                    response.behavior.allowsReminderNotification shouldBe false
                    response.ai.defaultProvider.id shouldBe provider.id
                    response.ai.defaultProvider.type shouldBe provider.type.name
                    response.ai.defaultModel.id shouldBe model.id
                    response.ai.defaultModel.model shouldBe model.model
                }
            }

            When("연동된 인증 제공자가 있으면") {
                lateinit var kakao: AuthProvider
                lateinit var naver: AuthProvider

                beforeTest {
                    kakao =
                        authProviderRepository.insert(
                            AuthProvider(
                                userId = user.id!!,
                                providerType = AuthProviderType.KAKAO,
                                providerId = "kakao-${RandomFixture.randomId()}",
                            ),
                        )
                    naver =
                        authProviderRepository.insert(
                            AuthProvider(
                                userId = user.id!!,
                                providerType = AuthProviderType.NAVER,
                                providerId = "naver-${RandomFixture.randomId()}",
                            ),
                        )
                }

                Then("연동된 인증 제공자 목록을 반환한다") {
                    val response = getMySettingsService.getMySettings(loginId = user.id!!)

                    response.providers shouldContainExactlyInAnyOrder
                        listOf(
                            UserAuthProviderResponse(id = kakao.id!!, type = AuthProviderType.KAKAO.name),
                            UserAuthProviderResponse(id = naver.id!!, type = AuthProviderType.NAVER.name),
                        )
                }
            }

            When("로그인 사용자가 없으면") {
                val get =
                    suspend {
                        getMySettingsService.getMySettings(loginId = RandomFixture.randomId())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }
    })

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
