package com.qlink.user.repository.table

import com.qlink.ai.repository.table.AiProviders
import com.qlink.ai.repository.table.AvailableModels
import com.qlink.auth.domain.Role
import com.qlink.user.domain.User
import com.qlink.user.domain.UserAccent
import com.qlink.user.domain.UserTheme
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 100)
    val nickname = varchar("nickname", 50)
    val role = enumerationByName<Role>("role", 20).default(Role.NORMAL)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val avatarEmoji = varchar("avatar_emoji", 20).nullable()
    val theme = varchar("theme", 1).default(UserTheme.LIGHT.code)
    val accent = varchar("accent", 1).default(UserAccent.GRAY.code)
    val allowsReminder = bool("allows_reminder").default(true)
    val defaultAiProviderId =
        reference("default_ai_provider_id", AiProviders.id, onDelete = ReferenceOption.SET_NULL)
            .nullable()
    val defaultModelId =
        reference("default_model_id", AvailableModels.id, onDelete = ReferenceOption.SET_NULL)
            .nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("users_username_unique", username)
    }
}

fun ResultRow.toUserDomain(): User =
    User(
        id = this[Users.id],
        username = this[Users.username],
        nickname = this[Users.nickname],
        role = this[Users.role],
        avatarUrl = this[Users.avatarUrl],
        avatarEmoji = this[Users.avatarEmoji],
        theme = UserTheme.fromCode(this[Users.theme]),
        accent = UserAccent.fromCode(this[Users.accent]),
        allowsReminder = this[Users.allowsReminder],
        defaultAiProviderId = this[Users.defaultAiProviderId],
        defaultModelId = this[Users.defaultModelId],
        createdAt = this[Users.createdAt].toKotlinInstant(),
        updatedAt = this[Users.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(user: User) {
    this[Users.username] = user.username
    this[Users.nickname] = user.nickname
    this[Users.role] = user.role
    this[Users.avatarUrl] = user.avatarUrl
    this[Users.avatarEmoji] = user.avatarEmoji
    this[Users.theme] = user.theme.code
    this[Users.accent] = user.accent.code
    this[Users.allowsReminder] = user.allowsReminder
    this[Users.defaultAiProviderId] = user.defaultAiProviderId
    this[Users.defaultModelId] = user.defaultModelId
}

fun UpdateBuilder<*>.refreshUserUpdatedAt() {
    this[Users.updatedAt] = Clock.System.now().toJavaInstant()
}
