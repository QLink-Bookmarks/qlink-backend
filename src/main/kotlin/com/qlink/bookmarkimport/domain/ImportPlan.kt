package com.qlink.bookmarkimport.domain

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

    fun toImportFolders(
        importId: Long,
        newFolderIdByName: Map<String, Long>,
    ): List<ImportFolder> =
        folders.map { folder ->
            ImportFolder.create(
                importId = importId,
                folderId = folder.savedFolderId(newFolderIdByName),
                kind = folder.kind,
                name = folder.name,
            )
        }

    fun toImportFailures(importId: Long): List<ImportFailure> = failures.map { it.toImportFailure(importId) }
}
