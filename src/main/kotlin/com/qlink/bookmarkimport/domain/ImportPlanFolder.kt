package com.qlink.bookmarkimport.domain

import kotlin.time.Instant

class ImportPlanFolder private constructor(
    val kind: ImportFolderKind,
    val folderId: Long?,
    val name: String,
    val createdAt: Instant?,
) {
    fun savedFolderId(newFolderIdByName: Map<String, Long>): Long = folderId ?: newFolderIdByName.getValue(name)

    companion object {
        fun existing(
            folderId: Long,
            name: String,
        ): ImportPlanFolder = ImportPlanFolder(kind = ImportFolderKind.EXISTING, folderId = folderId, name = name, createdAt = null)

        fun new(
            name: String,
            createdAt: Instant,
        ): ImportPlanFolder = ImportPlanFolder(kind = ImportFolderKind.NEW, folderId = null, name = name, createdAt = createdAt)
    }
}
