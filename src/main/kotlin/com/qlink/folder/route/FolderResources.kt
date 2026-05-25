package com.qlink.folder.route

import io.ktor.resources.Resource

@Resource("/folders")
class FolderResources {
    @Resource("{id}")
    class ById(
        val parent: FolderResources = FolderResources(),
        val id: Long,
        val onDelete: String? = null,
    )
}
