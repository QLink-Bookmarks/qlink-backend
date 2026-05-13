package com.qlink.auth.repository.table

import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object AuthProviders : Table("auth_providers") {
  val id = long("id").autoIncrement()
  val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
  val providerType = varchar("provider_type", 20)
  val providerId = varchar("provider_id", 255)
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")

  override val primaryKey = PrimaryKey(id)

  init {
    uniqueIndex("auth_providers_provider_unique", providerType, providerId)
    index("auth_providers_user_id_idx", false, userId)
  }
}
