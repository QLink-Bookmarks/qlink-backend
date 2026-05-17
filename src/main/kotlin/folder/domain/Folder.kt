package com.qlink.folder.domain

import kotlin.time.Instant

data class Folder(
    val id: Long,
    val ownerId: Long,
    val name: String,
    val emoji: String?,
    val sharedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
