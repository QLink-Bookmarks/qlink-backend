package com.qlink.device.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DevicePlatformTest :
    BehaviorSpec({
        Given("DevicePlatform 요청 이름 변환 테스트") {
            When("지원하는 플랫폼 이름이면") {
                Then("DevicePlatform으로 변환한다") {
                    DevicePlatform.fromRequestName("ios") shouldBe DevicePlatform.IOS
                    DevicePlatform.fromRequestName("android") shouldBe DevicePlatform.ANDROID
                }
            }

            When("지원하지 않는 플랫폼 이름이면") {
                val convert = {
                    DevicePlatform.fromRequestName("web")
                }

                Then("DEVICE_PLATFORM_NOT_SUPPORTED 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.DEVICE_PLATFORM_NOT_SUPPORTED.message) {
                        convert()
                    }
                }
            }
        }
    })
