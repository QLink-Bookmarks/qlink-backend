package com.qlink.link.route

import io.ktor.resources.Resource

@Resource("/links")
class LinkResources {
    @Resource("{id}")
    class ById(
        val parent: LinkResources = LinkResources(),
        val id: Long,
    )
}
