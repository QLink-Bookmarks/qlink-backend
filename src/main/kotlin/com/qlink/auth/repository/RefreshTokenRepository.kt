package com.qlink.auth.repository

import com.qlink.auth.domain.RefreshToken
import kotlin.time.Instant

interface RefreshTokenRepository {
    suspend fun insert(refreshToken: RefreshToken): RefreshToken

    suspend fun findByToken(token: String): RefreshToken?

    suspend fun findLatestByUserIdAndFamilyId(
        userId: Long,
        familyId: String,
    ): RefreshToken?

    suspend fun rotateByTokenAndFamily(
        currentToken: String,
        userId: Long,
        familyId: String,
        newToken: String,
        issuedAt: Instant,
        expiredAt: Instant,
    ): RefreshToken?
}
