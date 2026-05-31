package com.qlink.ai.repository.table

import com.qlink.ai.domain.UserProvider
import com.qlink.ai.domain.UserProviderRole
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

object UserProviders : Table("user_providers") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val providerId = reference("provider_id", AiProviders.id, onDelete = ReferenceOption.CASCADE)
    val role = enumerationByName<UserProviderRole>("role", 20)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("user_providers_provider_id_idx", false, providerId)
        uniqueIndex("user_providers_user_provider_unique", userId, providerId)
    }
}

fun ResultRow.toUserProviderDomain(): UserProvider =
    UserProvider(
        id = this[UserProviders.id],
        userId = this[UserProviders.userId],
        providerId = this[UserProviders.providerId],
        role = this[UserProviders.role],
        createdAt = this[UserProviders.createdAt].toKotlinInstant(),
        updatedAt = this[UserProviders.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(userProvider: UserProvider) {
    this[UserProviders.userId] = userProvider.userId
    this[UserProviders.providerId] = userProvider.providerId
    this[UserProviders.role] = userProvider.role
}

fun UpdateBuilder<*>.refreshUserProviderUpdatedAt() {
    this[UserProviders.updatedAt] = Clock.System.now().toJavaInstant()
}
