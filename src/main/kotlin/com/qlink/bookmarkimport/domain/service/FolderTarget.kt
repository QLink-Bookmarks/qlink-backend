package com.qlink.bookmarkimport.domain.service

import com.qlink.bookmarkimport.domain.ImportPlanFolder
import kotlin.time.Instant

internal data class FolderTarget(
    val folderId: Long?,
    val name: String,
) {
    fun toPlanFolder(createdAt: Instant): ImportPlanFolder =
        folderId?.let { ImportPlanFolder.existing(folderId = it, name = name) } ?: ImportPlanFolder.new(name = name, createdAt = createdAt)
}
