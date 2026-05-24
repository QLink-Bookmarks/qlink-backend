package com.qlink.link.route

import io.ktor.resources.Resource

@Resource("/links")
class LinkResources(
    val query: String? = null,
    val folderId: Long? = null,
    val order: String = "latest",
    val cursor: String? = null,
    val size: Int = 15,
) {
    @Resource("{id}")
    class ById(
        val parent: LinkResources = LinkResources(),
        val id: Long,
    )
}
