package com.qlink.auth.service

import com.qlink.auth.domain.RefreshToken
import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class SignOutServiceTest :
    BaseServiceTest({
        val service = koinGet<SignOutService>()
        val userRepository = koinGet<UserRepository>()
        val refreshTokenRepository = koinGet<RefreshTokenRepository>()

        Given("로그아웃 요청이 주어졌을 때") {
            When("로그인 사용자의 refresh token이면") {
                val user = insertUser(userRepository)
                val userId = requireNotNull(user.id)
                val refreshToken = insertRefreshToken(refreshTokenRepository, userId).token

                val signOut =
                    suspend {
                        service.signOut(userId, refreshToken)
                    }

                Then("refresh token을 삭제하고 성공한다") {
                    shouldNotThrow<BusinessException> { signOut() }
                    refreshTokenRepository.findByToken(refreshToken).shouldBeNull()
                }
            }

            When("이미 삭제된 refresh token이면") {
                val user = insertUser(userRepository)
                val userId = requireNotNull(user.id)

                val signOut =
                    suspend {
                        service.signOut(userId, "already-removed-${RandomFixture.randomId()}")
                    }

                Then("예외 없이 성공한다") {
                    shouldNotThrow<BusinessException> { signOut() }
                }
            }

            When("다른 사용자의 refresh token이면") {
                val user = insertUser(userRepository)
                val userId = requireNotNull(user.id)
                val other = insertUser(userRepository)
                val otherToken = insertRefreshToken(refreshTokenRepository, requireNotNull(other.id)).token

                val signOut =
                    suspend {
                        service.signOut(userId, otherToken)
                    }

                Then("해당 refresh token은 삭제되지 않는다") {
                    shouldNotThrow<BusinessException> { signOut() }
                    refreshTokenRepository.findByToken(otherToken).shouldNotBeNull()
                }
            }

            When("로그인 사용자가 없으면") {
                val signOut =
                    suspend {
                        service.signOut(RandomFixture.randomId(), "refresh-token")
                    }

                Then("사용자 없음 예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.USER_NOT_FOUND.message) {
                        signOut()
                    }
                }
            }
        }
    })

private suspend fun insertUser(userRepository: UserRepository): User =
    userRepository.insert(
        User(
            username = "user-${RandomFixture.randomId()}",
            nickname = RandomFixture.randomSentenceWithMax(50),
        ),
    )

private suspend fun insertRefreshToken(
    refreshTokenRepository: RefreshTokenRepository,
    userId: Long,
): RefreshToken {
    val now = Clock.System.now()
    return refreshTokenRepository.insert(
        RefreshToken(
            userId = userId,
            familyId = "family-${RandomFixture.randomId()}",
            token = "refresh-${RandomFixture.randomId()}",
            issuedAt = now,
            expiredAt = now + 10.minutes,
        ),
    )
}
