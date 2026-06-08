package com.qlink.auth.domain

import kotlin.time.Duration
import kotlin.time.Instant

data class RefreshToken(
    val id: Long? = null,
    val userId: Long,
    val familyId: String,
    val token: String,
    val issuedAt: Instant,
    val expiredAt: Instant,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    fun rotate(
        token: String,
        issuedAt: Instant,
        expiredAt: Instant,
    ): RefreshToken =
        copy(
            token = token,
            issuedAt = issuedAt,
            expiredAt = expiredAt,
        )

    fun issuedWithin(
        now: Instant,
        duration: Duration,
    ): Boolean = now - issuedAt <= duration

    fun isExpired(now: Instant): Boolean = expiredAt <= now
}
