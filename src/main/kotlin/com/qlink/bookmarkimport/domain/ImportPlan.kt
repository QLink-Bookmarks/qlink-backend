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
}
