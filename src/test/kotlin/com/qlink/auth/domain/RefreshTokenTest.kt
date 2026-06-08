package com.qlink.auth.domain

import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RefreshTokenTest :
    BehaviorSpec({
        Given("refresh token 도메인이 있을 때") {
            val now = Clock.System.now()
            val refreshToken =
                RefreshToken(
                    id = RandomFixture.randomId(),
                    userId = RandomFixture.randomId(),
                    familyId = "family-${RandomFixture.randomId()}",
                    token = "old-token",
                    issuedAt = now,
                    expiredAt = now + 10.minutes,
                    createdAt = now,
                    updatedAt = now,
                )

            When("새 token으로 회전하면") {
                val issuedAt = now + 10.seconds
                val expiredAt = now + 10.minutes
                val rotated =
                    refreshToken.rotate(
                        token = "new-token",
                        issuedAt = issuedAt,
                        expiredAt = expiredAt,
                    )

                Then("token과 발급/갱신 시간을 변경한다") {
                    rotated.token shouldBe "new-token"
                    rotated.issuedAt shouldBe issuedAt
                    rotated.expiredAt shouldBe expiredAt
                    rotated.userId shouldBe refreshToken.userId
                    rotated.familyId shouldBe refreshToken.familyId
                }
            }

            When("갱신 시간이 허용 범위 안이면") {
                val valid = refreshToken.issuedWithin(now + 30.seconds, 1.minutes)

                Then("최근 갱신으로 판단한다") {
                    valid.shouldBeTrue()
                }
            }

            When("갱신 시간이 허용 범위를 지나면") {
                val valid = refreshToken.issuedWithin(now + 2.minutes, 1.minutes)

                Then("최근 갱신이 아니라고 판단한다") {
                    valid.shouldBeFalse()
                }
            }

            When("만료 시간이 지났으면") {
                val expired = refreshToken.isExpired(now + 11.minutes)

                Then("만료됐다고 판단한다") {
                    expired.shouldBeTrue()
                }
            }
        }
    })
