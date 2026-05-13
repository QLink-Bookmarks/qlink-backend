package com.qlink.link.domain

import java.time.OffsetDateTime

data class Link(
  val id: Long,
  val ownerId: Long,
  val folderId: Long,
  val url: String,
  val title: String,
  val summary: String?,
  val oneLiner: String?,
  val tags: List<String>,
  val thumbnailUrl: String?,
  val sourceType: String,
  val reminderAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)
