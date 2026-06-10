package com.qlink.user.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.dto.UpdateMyProfileRequest
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe

class UpdateMyProfileServiceTest :
    BaseServiceTest({
        val updateMyProfileService = koinGet<UpdateMyProfileService>()
        val userRepository = koinGet<UserRepository>()

        Given("내 정보 수정 서비스 테스트") {
            lateinit var user: User
            lateinit var anotherUser: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                anotherUser = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("내 정보를 수정하면") {
                val username = "user${RandomFixture.randomId()}"
                val nickname = RandomFixture.randomSentenceWithMax(50)
                val avatarUrl = RandomFixture.randomUrl()
                val avatarEmoji = RandomFixture.randomEmoji()
                val update =
                    suspend {
                        updateMyProfileService.updateMyProfile(
                            loginId = user.id!!,
                            request =
                                UpdateMyProfileRequest(
                                    username = username,
                                    nickname = nickname,
                                    avatarUrl = avatarUrl,
                                    avatarEmoji = avatarEmoji,
                                ),
                        )
                    }

                Then("요청한 프로필 정보로 변경한다") {
                    shouldNotThrow<BusinessException> {
                        update()
                    }

                    val updated = userRepository.findById(user.id!!)!!

                    updated.username shouldBe username
                    updated.nickname shouldBe nickname
                    updated.avatarUrl shouldBe avatarUrl
                    updated.avatarEmoji shouldBe avatarEmoji
                }
            }

            When("기존 사용자 이름과 같은 값으로 수정하면") {
                val nickname = RandomFixture.randomSentenceWithMax(50)
                val update =
                    suspend {
                        updateMyProfileService.updateMyProfile(
                            loginId = user.id!!,
                            request =
                                UpdateMyProfileRequest(
                                    username = user.username,
                                    nickname = nickname,
                                    avatarUrl = null,
                                    avatarEmoji = null,
                                ),
                        )
                    }

                Then("중복 예외 없이 변경한다") {
                    shouldNotThrow<BusinessException> {
                        update()
                    }

                    val updated = userRepository.findById(user.id!!)!!

                    updated.username shouldBe user.username
                    updated.nickname shouldBe nickname
                    updated.avatarUrl shouldBe null
                    updated.avatarEmoji shouldBe null
                }
            }

            When("로그인 사용자가 없으면") {
                val update =
                    suspend {
                        updateMyProfileService.updateMyProfile(
                            loginId = RandomFixture.randomId(),
                            request =
                                UpdateMyProfileRequest(
                                    username = "user${RandomFixture.randomId()}",
                                    nickname = RandomFixture.randomSentenceWithMax(50),
                                    avatarUrl = RandomFixture.randomUrl(),
                                    avatarEmoji = RandomFixture.randomEmoji(),
                                ),
                        )
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("본인 제외 같은 사용자 이름이 존재하면") {
                val update =
                    suspend {
                        updateMyProfileService.updateMyProfile(
                            loginId = user.id!!,
                            request =
                                UpdateMyProfileRequest(
                                    username = anotherUser.username,
                                    nickname = RandomFixture.randomSentenceWithMax(50),
                                    avatarUrl = RandomFixture.randomUrl(),
                                    avatarEmoji = RandomFixture.randomEmoji(),
                                ),
                        )
                    }

                Then("사용자 이름 중복 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_USERNAME_DUPLICATED.message) {
                        update()
                    }
                }
            }
        }
    })
