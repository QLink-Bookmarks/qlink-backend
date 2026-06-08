package com.qlink.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.config.SecurityConfig
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

data class RefreshTokenClaims(
    val userId: Long,
    val familyId: String,
)

class AuthTokenService(
    private val securityConfig: SecurityConfig,
) {
    private val algorithm = Algorithm.HMAC256(securityConfig.jwtSecret)
    private val accessTokenLifetime = securityConfig.accessDurationSeconds.seconds
    private val refreshTokenLifetime = securityConfig.refreshDurationSeconds.seconds

    fun issueAccessToken(
        userId: Long,
        role: Role,
    ): String {
        val now = Clock.System.now()

        return JWT
            .create()
            .withSubject(userId.toString())
            .withClaim("role", role.name)
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from((now + accessTokenLifetime).toJavaInstant()))
            .sign(algorithm)
    }

    fun issueRefreshToken(
        userId: Long,
        familyId: String = UUID.randomUUID().toString(),
    ): String {
        val jwt =
            JWT
                .create()
                .withSubject(userId.toString())
                .withClaim("familyId", familyId)
                .withIssuedAt(Date.from(Clock.System.now().toJavaInstant()))
                .sign(algorithm)

        return Base64.getUrlEncoder().withoutPadding().encodeToString(jwt.toByteArray(Charsets.UTF_8))
    }

    fun refreshTokenExpiredAt(issuedAt: Instant): Instant = issuedAt + refreshTokenLifetime

    fun verifyRefreshToken(refreshToken: String): RefreshTokenClaims {
        val jwt =
            runCatching {
                String(Base64.getUrlDecoder().decode(refreshToken), Charsets.UTF_8)
            }.getOrElse {
                throw BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, it)
            }

        val decoded =
            try {
                JWT
                    .require(algorithm)
                    .build()
                    .verify(jwt)
            } catch (exception: JWTVerificationException) {
                throw BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, exception)
            } catch (exception: IllegalArgumentException) {
                throw BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, exception)
            }

        val userId = decoded.subject?.toLongOrNull() ?: throw BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS)
        val familyId =
            decoded
                .getClaim("familyId")
                .asString()
                ?.takeIf { it.isNotBlank() }
                ?: throw BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS)

        return RefreshTokenClaims(
            userId = userId,
            familyId = familyId,
        )
    }
}
