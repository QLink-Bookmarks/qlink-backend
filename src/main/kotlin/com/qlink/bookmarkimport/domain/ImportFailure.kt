package com.qlink.bookmarkimport.domain

import kotlin.time.Instant

class ImportFailure(
    val id: Long? = null,
    val importId: Long,
    val title: String,
    val url: String,
    val reason: ImportFailureReason,
    val createdAt: Instant? = null,
)
