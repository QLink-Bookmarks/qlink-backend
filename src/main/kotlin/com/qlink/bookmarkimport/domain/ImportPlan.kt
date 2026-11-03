package com.qlink.bookmarkimport.domain

import kotlin.time.Instant

class ImportPlan(
    val ownerId: Long,
    val folders: List<ImportPlanFolder>,
    val links: List<ImportPlanLink>,
    val failures: List<ImportPlanFailure>,
    val skippedCount: Int,
) {
    val newFolders: List<ImportPlanFolder>
        get() = folders.filter { it.kind == ImportFolderKind.NEW }

    val linkCount: Int
        get() = links.size

    val folderCount: Int
        get() = newFolders.size

    val failedCount: Int
        get() = failures.size

    fun folderIdOf(
        link: ImportPlanLink,
        newFolderIdByName: Map<String, Long>,
    ): Long? = link.folder?.savedFolderIdIn(newFolderIdByName)

    fun toImportFolders(
        importId: Long,
        newFolderIdByName: Map<String, Long>,
    ): List<ImportFolder> =
        folders.map { folder ->
            ImportFolder.create(
                importId = importId,
                folderId = folder.savedFolderIdIn(newFolderIdByName),
                kind = folder.kind,
                name = folder.name,
            )
        }

    fun toImportFailures(importId: Long): List<ImportFailure> =
        failures.map {
            ImportFailure(
                importId = importId,
                title = it.title,
                url = it.url,
                reason = it.reason,
            )
        }

    private fun ImportPlanFolder.savedFolderIdIn(newFolderIdByName: Map<String, Long>): Long = folderId ?: newFolderIdByName.getValue(name)
}

class ImportPlanFolder private constructor(
    val kind: ImportFolderKind,
    val folderId: Long?,
    val name: String,
    val createdAt: Instant?,
) {
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

class ImportPlanLink(
    val url: String,
    val title: String,
    val createdAt: Instant,
    val folder: ImportPlanFolder?,
)

class ImportPlanFailure(
    val title: String,
    val url: String,
    val reason: ImportFailureReason,
)
