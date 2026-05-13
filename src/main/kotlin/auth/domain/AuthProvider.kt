package com.qlink.auth.domain

import java.time.OffsetDateTime

data class AuthProvider(
  val id: Long,
  val userId: Long,
  val providerType: String,
  val providerId: String,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)
