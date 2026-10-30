package com.qlink.auth.domain

import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.RefreshTokenFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RefreshTokenTest :
    BehaviorSpec({
        Given("refresh token 도메인이 있을 때") {
            val now = Clock.System.now()
            val refreshToken =
                RefreshTokenFixture.createRandomValidRefreshToken(
                    issuedAt = now,
                    expiredAt = now + 10.minutes,
                )

            When("새 token으로 회전하면") {
                val issuedAt = now + 10.seconds
                val expiredAt = now + 10.minutes
                val newToken = RandomFixture.randomFixedSentence(32)
                val rotated =
                    refreshToken.rotate(
                        token = newToken,
                        issuedAt = issuedAt,
                        expiredAt = expiredAt,
                    )

                Then("token과 발급/갱신 시간을 변경한다") {
                    rotated.shouldNotBeNull()
                    rotated.token shouldBe newToken
                    rotated.issuedAt shouldBe issuedAt
                    rotated.expiredAt shouldBe expiredAt
                    rotated.userId shouldBe refreshToken.userId
                    rotated.familyId shouldBe refreshToken.familyId
                }
            }

            When("정확히 만료 시각에 회전하면") {
                val expired =
                    RefreshTokenFixture.createRandomValidRefreshToken(
                        issuedAt = now - 1.days,
                        expiredAt = now,
                    )
                val rotated =
                    expired.rotate(
                        token = RandomFixture.randomFixedSentence(32),
                        issuedAt = now,
                        expiredAt = now + 14.days,
                    )

                Then("회전 결과가 없다") {
                    rotated.shouldBeNull()
                }
            }

            When("최신 발급 후 정확히 1분이고 만료 전이면") {
                val latest =
                    RefreshTokenFixture.createRandomValidRefreshToken(
                        issuedAt = now - 1.minutes,
                        expiredAt = now + 1.days,
                    )

                Then("회전 유예 안이다") {
                    latest.isWithinRefreshGrace(now = now, duration = 1.minutes).shouldBeTrue()
                }
            }

            When("최신 발급 후 1분을 넘으면") {
                val latest =
                    RefreshTokenFixture.createRandomValidRefreshToken(
                        issuedAt = now - 1.minutes - 1.seconds,
                        expiredAt = now + 1.days,
                    )

                Then("회전 유예 밖이다") {
                    latest.isWithinRefreshGrace(now = now, duration = 1.minutes).shouldBeFalse()
                }
            }

            When("발급 후 1분 이내지만 만료됐으면") {
                val latest =
                    RefreshTokenFixture.createRandomValidRefreshToken(
                        issuedAt = now - 30.seconds,
                        expiredAt = now,
                    )

                Then("회전 유예 밖이다") {
                    latest.isWithinRefreshGrace(now = now, duration = 1.minutes).shouldBeFalse()
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
