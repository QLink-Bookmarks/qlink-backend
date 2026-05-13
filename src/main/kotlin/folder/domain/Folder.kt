package com.qlink.folder.domain

import java.time.OffsetDateTime

data class Folder(
  val id: Long,
  val ownerId: Long,
  val name: String,
  val emoji: String?,
  val sharedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)
