package com.qlink.link.route

import com.qlink.link.dto.DEFAULT_LINK_SEARCH_ORDER
import io.ktor.resources.Resource

@Resource("/links")
class LinkResources(
    val query: String? = null,
    val folderId: Long? = null,
    val order: String = DEFAULT_LINK_SEARCH_ORDER,
    val cursor: String? = null,
    val size: Int = 15,
) {
    @Resource("ai")
    class Ai(
        val parent: LinkResources = LinkResources(),
    )

    @Resource("{id}")
    class ById(
        val parent: LinkResources = LinkResources(),
        val id: Long,
    )
}
