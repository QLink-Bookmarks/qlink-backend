package com.qlink.bookmarkimport.domain

import kotlin.time.Instant

class ImportPlanLink(
    val url: String,
    val title: String,
    val createdAt: Instant,
    val folder: ImportPlanFolder?,
) {
    fun savedFolderId(newFolderIdByName: Map<String, Long>): Long? = folder?.savedFolderId(newFolderIdByName)
}
