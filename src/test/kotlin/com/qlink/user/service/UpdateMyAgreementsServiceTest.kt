package com.qlink.user.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.dto.UpdateMyAgreementsRequest
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class UpdateMyAgreementsServiceTest :
    BaseServiceTest({
        val updateMyAgreementsService = koinGet<UpdateMyAgreementsService>()
        val userRepository = koinGet<UserRepository>()

        Given("필수 동의 변경 서비스 테스트") {
            lateinit var user: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("동의 값을 true로 변경하면") {
                Then("사용자 동의 정보가 갱신된다") {
                    updateMyAgreementsService.updateMyAgreements(
                        loginId = user.id!!,
                        request = UpdateMyAgreementsRequest(allowsPrivacy = true, allowsAiUsage = true),
                    )

                    val updated = userRepository.findById(user.id!!)
                    updated.shouldNotBeNull()
                    updated.allowsPrivacy shouldBe true
                    updated.allowsAiUsage shouldBe true
                }
            }

            When("요청에 값이 없으면") {
                Then("동의 값은 false로 간주되어 갱신된다") {
                    updateMyAgreementsService.updateMyAgreements(
                        loginId = user.id!!,
                        request = UpdateMyAgreementsRequest(),
                    )

                    val updated = userRepository.findById(user.id!!)
                    updated.shouldNotBeNull()
                    updated.allowsPrivacy shouldBe false
                    updated.allowsAiUsage shouldBe false
                }
            }

            When("로그인 사용자가 없으면") {
                val update =
                    suspend {
                        updateMyAgreementsService.updateMyAgreements(
                            loginId = RandomFixture.randomId(),
                            request = UpdateMyAgreementsRequest(allowsPrivacy = true, allowsAiUsage = true),
                        )
                    }

                Then("USER_NOT_FOUND 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        update()
                    }
                }
            }
        }
    })
