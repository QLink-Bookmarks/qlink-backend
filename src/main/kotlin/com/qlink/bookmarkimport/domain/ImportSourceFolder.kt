package com.qlink.bookmarkimport.domain

import com.qlink.common.text.toNfc
import kotlin.time.Instant

class ImportSourceFolder(
    val key: String,
    val folderId: Long? = null,
    val name: String,
    val createdAt: Instant? = null,
) {
    val comparableName: String
        get() = comparableNameOf(name)

    fun createdAtOr(now: Instant): Instant = createdAt?.coerceAtMost(now) ?: now

    companion object {
        const val NAME_MAX_LENGTH = 100

        fun comparableNameOf(name: String): String = name.trim().toNfc()
    }
}
