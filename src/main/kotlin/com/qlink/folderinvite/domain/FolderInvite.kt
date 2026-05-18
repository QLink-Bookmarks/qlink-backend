package com.qlink.folderinvite.domain

import kotlin.time.Instant

data class FolderInvite(
    val id: Long,
    val folderId: Long,
    val inviterId: Long,
    val token: String,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
