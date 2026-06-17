package com.qlink.auth.service

import com.qlink.auth.dto.AuthTokenResponse
import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class RefreshAuthTokenService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val authTokenService: AuthTokenService,
) {
    suspend fun refresh(refreshToken: String?): AuthTokenResponse {
        val token = refreshToken?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_MISSING)
        val claims = authTokenService.verifyRefreshToken(token)
        val now = Clock.System.now()

        return tx.required {
            val user = userRepository.findById(claims.userId) ?: throw BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID)
            val newRefreshToken =
                authTokenService.issueRefreshToken(
                    userId = claims.userId,
                    familyId = claims.familyId,
                )
            val newExpiredAt = authTokenService.refreshTokenExpiredAt(now)
            val rotated =
                refreshTokenRepository.rotateByTokenAndFamily(
                    currentToken = token,
                    userId = claims.userId,
                    familyId = claims.familyId,
                    newToken = newRefreshToken,
                    issuedAt = now,
                    expiredAt = newExpiredAt,
                )

            if (rotated != null) {
                return@required user.toAuthTokenResponse(refreshToken = newRefreshToken)
            }

            val latest =
                refreshTokenRepository.findLatestByUserIdAndFamilyId(
                    userId = claims.userId,
                    familyId = claims.familyId,
                ) ?: throw BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_REUSED)

            if (latest.isExpired(now) || !latest.issuedWithin(now = now, duration = 1.minutes)) {
                throw BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_REUSED)
            }

            user.toAuthTokenResponse(refreshToken = latest.token)
        }
    }

    private fun User.toAuthTokenResponse(refreshToken: String): AuthTokenResponse {
        val userId = requireNotNull(id)

        return AuthTokenResponse(
            accessToken =
                authTokenService.issueAccessToken(
                    userId = userId,
                    role = role,
                ),
            refreshToken = refreshToken,
        )
    }
}
