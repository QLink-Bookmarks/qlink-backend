package com.qlink.device.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.device.domain.DevicePlatform
import com.qlink.device.dto.PutDeviceRequest
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.DeviceTokenFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class PutDeviceServiceTest :
    BaseServiceTest({
        val service = koinGet<PutDeviceService>()
        val userRepository = koinGet<UserRepository>()
        val deviceTokenRepository = koinGet<DeviceTokenRepository>()

        Given("디바이스 토큰 등록 서비스 테스트") {
            lateinit var user: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("사용자가 신규 디바이스 토큰을 등록하면") {
                Then("토큰을 저장하고 등록 ID를 반환한다") {
                    val request =
                        PutDeviceRequest(
                            platform = DevicePlatform.NATIVE.name,
                            token = DeviceTokenFixture.randomDeviceToken(),
                        )

                    val response = service.putDevice(user.id!!, request)

                    val actual = deviceTokenRepository.findByToken(request.token)!!
                    response.id shouldBe actual.id
                    actual.userId shouldBe user.id
                    actual.platform shouldBe DevicePlatform.NATIVE
                    actual.token shouldBe request.token
                }
            }

            When("이미 등록된 토큰을 다시 등록하면") {
                Then("기존 디바이스 토큰을 그대로 반환한다") {
                    val token = DeviceTokenFixture.randomDeviceToken()
                    val firstRequest =
                        PutDeviceRequest(
                            platform = DevicePlatform.NATIVE.name,
                            token = token,
                        )
                    val secondRequest =
                        PutDeviceRequest(
                            platform = DevicePlatform.WEB.name,
                            token = token,
                        )

                    val firstResponse = service.putDevice(user.id!!, firstRequest)
                    val secondResponse = service.putDevice(user.id!!, secondRequest)

                    val actual = deviceTokenRepository.findByToken(token)!!
                    firstResponse.id shouldBe secondResponse.id
                    secondResponse.id shouldBe actual.id
                    actual.userId shouldBe user.id
                    actual.platform shouldBe DevicePlatform.NATIVE
                    deviceTokenRepository.findAllByUserId(user.id!!) shouldHaveSize 1
                }
            }

            When("기존 토큰을 다른 사용자가 등록하면") {
                Then("같은 토큰의 소유자를 새 로그인 사용자로 덮어쓴다") {
                    val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                    val token = DeviceTokenFixture.randomDeviceToken()

                    val firstResponse =
                        service.putDevice(
                            loginId = user.id!!,
                            request =
                                PutDeviceRequest(
                                    platform = DevicePlatform.NATIVE.name,
                                    token = token,
                                ),
                        )
                    val response =
                        service.putDevice(
                            loginId = otherUser.id!!,
                            request =
                                PutDeviceRequest(
                                    platform = DevicePlatform.NATIVE.name,
                                    token = token,
                                ),
                        )

                    val actual = deviceTokenRepository.findByToken(token)!!
                    response.id shouldBe firstResponse.id
                    response.id shouldBe actual.id
                    actual.userId shouldBe otherUser.id
                    deviceTokenRepository.findAllByUserId(user.id!!) shouldHaveSize 0
                    deviceTokenRepository.findAllByUserId(otherUser.id) shouldHaveSize 1
                }
            }

            When("로그인 사용자가 없으면") {
                val put =
                    suspend {
                        service.putDevice(
                            loginId = RandomFixture.randomId(),
                            request =
                                PutDeviceRequest(
                                    platform = DevicePlatform.NATIVE.name,
                                    token = DeviceTokenFixture.randomDeviceToken(),
                                ),
                        )
                    }

                Then("USER_NOT_FOUND 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        put()
                    }
                }
            }

            When("지원하지 않는 플랫폼이면") {
                val put =
                    suspend {
                        service.putDevice(
                            loginId = user.id!!,
                            request =
                                PutDeviceRequest(
                                    platform = "IOS",
                                    token = DeviceTokenFixture.randomDeviceToken(),
                                ),
                        )
                    }

                Then("DEVICE_PLATFORM_NOT_SUPPORTED 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.DEVICE_PLATFORM_NOT_SUPPORTED.message) {
                        put()
                    }
                }
            }
        }
    })
