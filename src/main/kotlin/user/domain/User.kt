package com.qlink.user.domain

import java.time.OffsetDateTime

data class User(
  val id: Long,
  val displayName: String,
  val avatarUrl: String?,
  val avatarEmoji: String?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)
