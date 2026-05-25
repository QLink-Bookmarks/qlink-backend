package com.qlink.folder.route

import com.qlink.folder.dto.DEFAULT_FOLDER_SEARCH_ORDER
import io.ktor.resources.Resource

@Resource("/folders")
class FolderResources(
    val query: String? = null,
    val order: String = DEFAULT_FOLDER_SEARCH_ORDER,
    val cursor: String? = null,
    val size: Int = 15,
) {
    @Resource("{id}")
    class ById(
        val parent: FolderResources = FolderResources(),
        val id: Long,
        val onDelete: String? = null,
    )
}
