package com.qlink.ai.domain

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class UserProviderTest :
    BehaviorSpec({
        Given("UserProvider 생성 테스트") {
            When("userRole을 지정하지 않으면") {
                val userProvider =
                    UserProvider(
                        userId = RandomFixture.randomId(),
                        providerId = RandomFixture.randomId(),
                        apiKey = "api-key",
                    )

                Then("NORMAL 역할을 기본값으로 가진다") {
                    userProvider.userRole shouldBe Role.NORMAL
                }
            }
        }

        Given("UserProvider 접근 권한 검증 테스트") {
            val ownerId = RandomFixture.randomId()

            When("본인 소유 제공자면") {
                val userProvider =
                    UserProvider(
                        userId = ownerId,
                        providerId = RandomFixture.randomId(),
                        apiKey = "api-key",
                    )

                Then("본인 접근이 허용된다") {
                    userProvider.isDefault shouldBe false
                    shouldNotThrowAny { userProvider.validateAccessibleBy(ownerId) }
                }
            }

            When("SUPER_ADMIN 기본 제공자면") {
                val userProvider =
                    UserProvider(
                        userId = ownerId,
                        providerId = RandomFixture.randomId(),
                        userRole = Role.SUPER_ADMIN,
                        apiKey = "api-key",
                    )

                Then("다른 사용자 접근도 허용된다") {
                    userProvider.isDefault shouldBe true
                    shouldNotThrowAny { userProvider.validateAccessibleBy(RandomFixture.randomId()) }
                }
            }

            When("본인 것도 기본도 아닌 제공자면") {
                val userProvider =
                    UserProvider(
                        userId = ownerId,
                        providerId = RandomFixture.randomId(),
                        apiKey = "api-key",
                    )

                Then("권한 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.AI_USER_PROVIDER_ACCESS_DENIED.message) {
                        userProvider.validateAccessibleBy(RandomFixture.randomId())
                    }
                }
            }
        }
    })
