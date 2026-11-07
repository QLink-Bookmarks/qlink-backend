package com.qlink.link.route

import io.ktor.resources.Resource

@Resource("/links")
class LinkResources(
    val query: String? = null,
    val folderId: Long? = null,
    val isFavorite: Boolean? = null,
    val orderKey: String? = null,
    val order: String? = null,
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
    ) {
        @Resource("favorite")
        class Favorite(
            val parent: ById,
        )

        @Resource("copy")
        class Copy(
            val parent: ById,
        )
    }
}
