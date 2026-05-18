package com.qlink.foldermember.domain

import kotlin.time.Instant

data class FolderMember(
    val folderId: Long,
    val userId: Long,
    val role: String,
    val joinedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
