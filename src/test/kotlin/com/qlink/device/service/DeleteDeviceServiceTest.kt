package com.qlink.device.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.device.domain.DevicePlatform
import com.qlink.device.domain.DeviceToken
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.DeviceTokenFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class DeleteDeviceServiceTest :
    BaseServiceTest({
        val service = koinGet<DeleteDeviceService>()
        val userRepository = koinGet<UserRepository>()
        val deviceTokenRepository = koinGet<DeviceTokenRepository>()

        suspend fun insertToken(
            userId: Long,
            token: String = DeviceTokenFixture.randomDeviceToken(),
        ): DeviceToken =
            deviceTokenRepository.insert(
                DeviceToken(
                    userId = userId,
                    platform = DevicePlatform.NATIVE,
                    token = token,
                ),
            )

        Given("디바이스 토큰 삭제 서비스 테스트") {
            lateinit var user: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("본인 디바이스 토큰을 삭제하면") {
                Then("토큰이 삭제된다") {
                    val token = insertToken(userId = user.id!!).token

                    service.deleteDevice(user.id!!, token)

                    deviceTokenRepository.findByToken(token).shouldBeNull()
                }
            }

            When("다른 사용자의 디바이스 토큰을 삭제하면") {
                Then("권한 예외를 반환하고 삭제하지 않는다") {
                    val otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                    val token = insertToken(userId = otherUser.id!!).token

                    val delete = suspend { service.deleteDevice(user.id!!, token) }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.DEVICE_DIFFERENT_OWNER.message) {
                        delete()
                    }
                    deviceTokenRepository.findByToken(token).shouldNotBeNull()
                }
            }

            When("존재하지 않는 토큰을 삭제하면") {
                Then("예외 없이 성공한다") {
                    shouldNotThrowAny {
                        service.deleteDevice(user.id!!, DeviceTokenFixture.randomDeviceToken())
                    }
                }
            }

            When("로그인 사용자가 없으면") {
                Then("USER_NOT_FOUND 예외를 반환한다") {
                    val token = insertToken(userId = user.id!!).token
                    val delete = suspend { service.deleteDevice(RandomFixture.randomId(), token) }

                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        delete()
                    }
                }
            }
        }
    })
