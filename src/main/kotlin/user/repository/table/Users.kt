package com.qlink.user.repository.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val displayName = varchar("display_name", 50)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val avatarEmoji = varchar("avatar_emoji", 20).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
