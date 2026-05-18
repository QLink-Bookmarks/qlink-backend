package com.qlink.user.repository.table

import com.qlink.user.domain.User
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
    val displayName = varchar("display_name", 50)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val avatarEmoji = varchar("avatar_emoji", 20).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toUserDomain(): User =
    User(
        id = this[Users.id],
        displayName = this[Users.displayName],
        avatarUrl = this[Users.avatarUrl],
        avatarEmoji = this[Users.avatarEmoji],
        createdAt = this[Users.createdAt].toKotlinInstant(),
        updatedAt = this[Users.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(user: User) {
    this[Users.displayName] = user.displayName
    this[Users.avatarUrl] = user.avatarUrl
    this[Users.avatarEmoji] = user.avatarEmoji
}
