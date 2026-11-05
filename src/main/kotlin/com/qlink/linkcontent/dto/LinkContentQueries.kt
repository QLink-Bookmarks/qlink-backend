@file:Suppress("ktlint:standard:filename")

package com.qlink.linkcontent.dto

import com.qlink.linkcontent.domain.ContentPriority
import kotlin.time.Instant

data class LinkContentTargetQuery(
    val contentId: Long,
    val linkId: Long,
    val url: String,
    val host: String,
    val keyset: DueKeyset,
)

data class LinkContentScheduleQuery(
    val due: Long,
    val pending: Long,
    val nextRunnableAt: Instant?,
    val oldestDueAgeSeconds: Long?,
)

data class DueKeyset(
    val priority: ContentPriority,
    val nextAttemptAt: Instant,
    val id: Long,
)
