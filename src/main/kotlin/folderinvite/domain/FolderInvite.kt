package com.qlink.folderinvite.domain

import java.time.OffsetDateTime

data class FolderInvite(
  val id: Long,
  val folderId: Long,
  val inviterId: Long,
  val token: String,
  val expiresAt: OffsetDateTime,
  val acceptedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)
