package com.qlink.user.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec

class UserTest :
    BehaviorSpec({
        Given("사용자 프로필 수정 테스트") {
            val user = UserFixture.createRandomValidUser()

            When("유효한 값으로 수정하면") {
                val update = {
                    user.changeProfile(
                        username = "user${RandomFixture.randomId()}",
                        nickname = RandomFixture.randomSentenceWithMax(50),
                        avatarUrl = RandomFixture.randomUrl(),
                        avatarEmoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("성공한다") {
                    shouldNotThrowAny {
                        update()
                    }
                }
            }

            When("사용자 이름이 비어 있으면") {
                val update = {
                    user.changeProfile(
                        username = " ",
                        nickname = RandomFixture.randomSentenceWithMax(50),
                        avatarUrl = RandomFixture.randomUrl(),
                        avatarEmoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_USERNAME_BLANK.message) {
                        update()
                    }
                }
            }

            When("사용자 이름이 최소 길이보다 짧으면") {
                val update = {
                    user.changeProfile(
                        username = RandomFixture.randomFixedSentence(2),
                        nickname = RandomFixture.randomSentenceWithMax(50),
                        avatarUrl = RandomFixture.randomUrl(),
                        avatarEmoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_USERNAME_UNDER_MIN.message) {
                        update()
                    }
                }
            }

            When("사용자 이름이 최대 길이를 초과하면") {
                val update = {
                    user.changeProfile(
                        username = RandomFixture.randomFixedSentence(101),
                        nickname = RandomFixture.randomSentenceWithMax(50),
                        avatarUrl = RandomFixture.randomUrl(),
                        avatarEmoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_USERNAME_OVER_MAX.message) {
                        update()
                    }
                }
            }

            When("닉네임이 비어 있으면") {
                val update = {
                    user.changeProfile(
                        username = "user${RandomFixture.randomId()}",
                        nickname = " ",
                        avatarUrl = RandomFixture.randomUrl(),
                        avatarEmoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NICKNAME_BLANK.message) {
                        update()
                    }
                }
            }

            When("닉네임이 최대 길이를 초과하면") {
                val update = {
                    user.changeProfile(
                        username = "user${RandomFixture.randomId()}",
                        nickname = RandomFixture.randomFixedSentence(51),
                        avatarUrl = RandomFixture.randomUrl(),
                        avatarEmoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NICKNAME_OVER_MAX.message) {
                        update()
                    }
                }
            }

            When("프로필 이모지 길이가 최대 길이를 초과하면") {
                val update = {
                    user.changeProfile(
                        username = "user${RandomFixture.randomId()}",
                        nickname = RandomFixture.randomSentenceWithMax(50),
                        avatarUrl = RandomFixture.randomUrl(),
                        avatarEmoji = RandomFixture.randomFixedSentence(21),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_AVATAR_EMOJI_OVER_MAX.message) {
                        update()
                    }
                }
            }

            When("프로필 이모지 형식이 잘못되면") {
                val update = {
                    user.changeProfile(
                        username = "user${RandomFixture.randomId()}",
                        nickname = RandomFixture.randomSentenceWithMax(50),
                        avatarUrl = RandomFixture.randomUrl(),
                        avatarEmoji = "avatar",
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_AVATAR_EMOJI_INVALID.message) {
                        update()
                    }
                }
            }
        }
    })
