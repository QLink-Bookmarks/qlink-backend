package com.qlink.device.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.DeviceTokenFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DeviceTokenTest :
    BehaviorSpec({
        Given("DeviceToken 생성 테스트") {
            When("유효한 값이면") {
                Then("디바이스 토큰을 생성한다") {
                    val token = DeviceTokenFixture.randomDeviceToken()
                    val deviceToken =
                        DeviceToken(
                            userId = RandomFixture.randomId(),
                            platform = DevicePlatform.IOS,
                            token = token,
                        )

                    deviceToken.token shouldBe token
                    deviceToken.platform shouldBe DevicePlatform.IOS
                }
            }

            When("토큰이 비어 있으면") {
                listOf("", "   ").forEach { token ->
                    Then("DEVICE_TOKEN_BLANK 예외를 반환한다") {
                        val create = {
                            DeviceTokenFixture.createRandomValidDeviceToken(token = token)
                        }

                        shouldThrowWithMessage<BusinessException>(ErrorCode.DEVICE_TOKEN_BLANK.message) {
                            create()
                        }
                    }
                }
            }

            When("토큰이 최대 길이를 초과하면") {
                val create = {
                    DeviceTokenFixture.createRandomValidDeviceToken(
                        token = RandomFixture.randomFixedSentence(DEVICE_TOKEN_MAX_LENGTH + 1),
                    )
                }

                Then("DEVICE_TOKEN_OVER_MAX 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.DEVICE_TOKEN_OVER_MAX.message) {
                        create()
                    }
                }
            }
        }
    })
