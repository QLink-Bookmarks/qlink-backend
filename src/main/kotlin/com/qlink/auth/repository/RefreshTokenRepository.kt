package com.qlink.auth.repository

import com.qlink.auth.domain.RefreshToken
import kotlin.time.Instant

interface RefreshTokenRepository {
    suspend fun insert(refreshToken: RefreshToken): RefreshToken

    suspend fun findByToken(token: String): RefreshToken?

    suspend fun findByTokenAndFamily(
        token: String,
        userId: Long,
        familyId: String,
    ): RefreshToken?

    suspend fun findLatestByUserIdAndFamilyId(
        userId: Long,
        familyId: String,
    ): RefreshToken?

    suspend fun rotateByTokenAndFamily(
        current: RefreshToken,
        rotated: RefreshToken,
        now: Instant,
    ): RefreshToken?

    suspend fun deleteByUserIdAndToken(
        userId: Long,
        token: String,
    )

    suspend fun deleteByUserIdAndFamilyId(
        userId: Long,
        familyId: String,
    )
}
