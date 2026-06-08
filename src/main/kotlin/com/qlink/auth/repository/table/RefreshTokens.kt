package com.qlink.auth.repository.table

import com.qlink.auth.domain.RefreshToken
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object RefreshTokens : Table("refresh_tokens") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val familyId = varchar("family_id", 36)
    val token = text("token")
    val issuedAt = timestamp("issued_at")
    val expiredAt = timestamp("expired_at")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("refresh_tokens_token_unique", token)
        index("refresh_tokens_user_family_idx", false, userId, familyId)
    }
}

fun ResultRow.toRefreshTokenDomain(): RefreshToken =
    RefreshToken(
        id = this[RefreshTokens.id],
        userId = this[RefreshTokens.userId],
        familyId = this[RefreshTokens.familyId],
        token = this[RefreshTokens.token],
        issuedAt = this[RefreshTokens.issuedAt].toKotlinInstant(),
        expiredAt = this[RefreshTokens.expiredAt].toKotlinInstant(),
        createdAt = this[RefreshTokens.createdAt].toKotlinInstant(),
        updatedAt = this[RefreshTokens.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(refreshToken: RefreshToken) {
    this[RefreshTokens.userId] = refreshToken.userId
    this[RefreshTokens.familyId] = refreshToken.familyId
    this[RefreshTokens.token] = refreshToken.token
    this[RefreshTokens.issuedAt] = refreshToken.issuedAt.toJavaInstant()
    this[RefreshTokens.expiredAt] = refreshToken.expiredAt.toJavaInstant()
}

fun UpdateBuilder<*>.refreshRefreshTokenUpdatedAt() {
    this[RefreshTokens.updatedAt] = Clock.System.now().toJavaInstant()
}
