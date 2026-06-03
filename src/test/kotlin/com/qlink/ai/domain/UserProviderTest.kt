package com.qlink.ai.domain

import com.qlink.auth.domain.Role
import com.qlink.support.fixture.RandomFixture
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
    })
