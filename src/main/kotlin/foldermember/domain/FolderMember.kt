package com.qlink.foldermember.domain

import java.time.OffsetDateTime

data class FolderMember(
  val folderId: Long,
  val userId: Long,
  val role: String,
  val joinedAt: OffsetDateTime,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)
