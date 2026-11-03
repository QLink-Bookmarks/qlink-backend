package com.qlink.bookmarkimport.domain

class ImportReversalTargets(
    val folders: List<ImportFolder>,
    val links: List<ImportLink>,
) {
    val folderIds: List<Long>
        get() = folders.map { it.folderId }

    val linkIds: List<Long>
        get() = links.map { it.linkId }

    val isEmpty: Boolean
        get() = folders.isEmpty() && links.isEmpty()
}
