package com.qlink.user.service

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
import io.kotest.matchers.shouldBe

class GetMyProfileServiceTest :
    BaseServiceTest({
        val getMyProfileService = koinGet<GetMyProfileService>()
        val userRepository = koinGet<UserRepository>()

        Given("내 정보 조회 서비스 테스트") {
            lateinit var user: User

            beforeTest {
                user =
                    userRepository.insert(
                        UserFixture
                            .createRandomValidUser()
                            .copyForProfile(role = Role.ADMIN),
                    )
            }

            When("현재 로그인 사용자를 조회하면") {
                val get =
                    suspend {
                        getMyProfileService.getMyProfile(loginId = user.id!!)
                    }

                Then("기본 정보와 role을 반환한다") {
                    val response = get()
                    val persisted = userRepository.findById(user.id!!)!!

                    response.id shouldBe persisted.id
                    response.username shouldBe persisted.username
                    response.nickname shouldBe persisted.nickname
                    response.role shouldBe Role.ADMIN
                    response.avatarUrl shouldBe persisted.avatarUrl
                    response.avatarEmoji shouldBe persisted.avatarEmoji
                }
            }

            When("로그인 사용자가 없으면") {
                val get =
                    suspend {
                        getMyProfileService.getMyProfile(loginId = RandomFixture.randomId())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        get()
                    }
                }
            }
        }
    })

private fun User.copyForProfile(role: Role): User =
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
