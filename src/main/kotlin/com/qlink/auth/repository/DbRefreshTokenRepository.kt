package com.qlink.auth.repository

import com.qlink.auth.domain.RefreshToken
import com.qlink.auth.repository.table.RefreshTokens
import com.qlink.auth.repository.table.fromDomain
import com.qlink.auth.repository.table.refreshRefreshTokenUpdatedAt
import com.qlink.auth.repository.table.toRefreshTokenDomain
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class DbRefreshTokenRepository : RefreshTokenRepository {
    override suspend fun insert(refreshToken: RefreshToken): RefreshToken =
        RefreshTokens
            .insertReturning {
                it.fromDomain(refreshToken)
            }.single()
            .toRefreshTokenDomain()

    override suspend fun findByToken(token: String): RefreshToken? =
        RefreshTokens
            .selectAll()
            .where { RefreshTokens.token eq token }
            .singleOrNull()
            ?.toRefreshTokenDomain()

    override suspend fun findLatestByUserIdAndFamilyId(
        userId: Long,
        familyId: String,
    ): RefreshToken? =
        RefreshTokens
            .selectAll()
            .where {
                (RefreshTokens.userId eq userId) and
                    (RefreshTokens.familyId eq familyId)
            }.orderBy(RefreshTokens.issuedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toRefreshTokenDomain()

    override suspend fun rotateByTokenAndFamily(
        currentToken: String,
        userId: Long,
        familyId: String,
        newToken: String,
        issuedAt: Instant,
        expiredAt: Instant,
    ): RefreshToken? =
        RefreshTokens
            .updateReturning(
                where = {
                    (RefreshTokens.token eq currentToken) and
                        (RefreshTokens.userId eq userId) and
                        (RefreshTokens.familyId eq familyId)
                },
            ) {
                it[RefreshTokens.token] = newToken
                it[RefreshTokens.issuedAt] = issuedAt.toJavaInstant()
                it[RefreshTokens.expiredAt] = expiredAt.toJavaInstant()
                it.refreshRefreshTokenUpdatedAt()
            }.singleOrNull()
            ?.toRefreshTokenDomain()
}
