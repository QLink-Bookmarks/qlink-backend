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

        val result =
            tx.required {
                val user = userRepository.findById(claims.userId) ?: throw BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID)
                val current =
                    refreshTokenRepository.findByTokenAndFamily(
                        token = token,
                        userId = claims.userId,
                        familyId = claims.familyId,
                    )

                if (current != null) {
                    val newToken =
                        authTokenService.issueRefreshToken(
                            userId = claims.userId,
                            familyId = claims.familyId,
                        )
                    val rotated =
                        current.rotate(
                            token = newToken,
                            issuedAt = now,
                            expiredAt = authTokenService.refreshTokenExpiredAt(now),
                        )

                    if (rotated == null) {
                        refreshTokenRepository.deleteByUserIdAndToken(userId = claims.userId, token = token)
                        return@required RefreshResult.Failure(ErrorCode.AUTH_REFRESH_TOKEN_INVALID)
                    }

                    val saved =
                        refreshTokenRepository.rotateByTokenAndFamily(
                            current = current,
                            rotated = rotated,
                            now = now,
                        )
                    if (saved != null) {
                        return@required RefreshResult.Success(user = user, refreshToken = saved.token)
                    }
                }

                val latest =
                    refreshTokenRepository.findLatestByUserIdAndFamilyId(
                        userId = claims.userId,
                        familyId = claims.familyId,
                    ) ?: return@required RefreshResult.Failure(ErrorCode.AUTH_REFRESH_TOKEN_REUSED)

                if (latest.isWithinRefreshGrace(now = now, duration = 1.minutes)) {
                    RefreshResult.Success(user = user, refreshToken = latest.token)
                } else {
                    refreshTokenRepository.deleteByUserIdAndFamilyId(userId = claims.userId, familyId = claims.familyId)
                    RefreshResult.Failure(ErrorCode.AUTH_REFRESH_TOKEN_REUSED)
                }
            }

        return when (result) {
            is RefreshResult.Success -> result.user.toAuthTokenResponse(refreshToken = result.refreshToken)
            is RefreshResult.Failure -> throw BusinessException(result.errorCode)
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

private sealed interface RefreshResult {
    data class Success(
        val user: User,
        val refreshToken: String,
    ) : RefreshResult

    data class Failure(
        val errorCode: ErrorCode,
    ) : RefreshResult
}
