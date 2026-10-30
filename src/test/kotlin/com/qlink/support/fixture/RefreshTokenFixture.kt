package com.qlink.support.fixture

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.qlink.auth.domain.RefreshToken
import com.qlink.config.SecurityConfig
import java.util.Base64
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant

object RefreshTokenFixture {
    fun createRandomValidRefreshToken(
        issuedAt: Instant = Clock.System.now(),
        expiredAt: Instant = issuedAt + 14.days,
    ): RefreshToken =
        RefreshToken(
            id = RandomFixture.randomId(),
            userId = RandomFixture.randomId(),
            familyId = RandomFixture.randomId().toString(),
            token = RandomFixture.randomFixedSentence(32),
            issuedAt = issuedAt,
            expiredAt = expiredAt,
            createdAt = issuedAt,
            updatedAt = issuedAt,
        )
}

object UserRefreshTokenFixture {
    fun createForUser(
        userId: Long,
        familyId: String,
        token: String,
        issuedAt: Instant,
        expiredAt: Instant = issuedAt + 14.days,
    ): RefreshToken =
        RefreshToken(
            userId = userId,
            familyId = familyId,
            token = token,
            issuedAt = issuedAt,
            expiredAt = expiredAt,
        )

    fun issueSignedTokenAt(
        securityConfig: SecurityConfig,
        userId: Long,
        familyId: String,
        issuedAt: Instant,
    ): String {
        val jwt =
            JWT
                .create()
                .withSubject(userId.toString())
                .withClaim("familyId", familyId)
                .withIssuedAt(Date.from(issuedAt.toJavaInstant()))
                .sign(Algorithm.HMAC256(securityConfig.jwtSecret))

        return Base64.getUrlEncoder().withoutPadding().encodeToString(jwt.toByteArray(Charsets.UTF_8))
    }
}
