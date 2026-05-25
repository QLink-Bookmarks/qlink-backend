@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PatchLinkTodoRequest(
    val id: Long? = null,
    val title: String,
    val reminderAt: Instant? = null,
)

@Serializable
data class PatchLinkRequest(
    val folderId: Long? = null,
    val memo: String? = null,
    val tags: List<String>? = null,
    val todos: List<PatchLinkTodoRequest>? = null,
)
