package com.qlink.post.route

import com.qlink.post.dto.DEFAULT_POST_SCROLL_SIZE
import com.qlink.post.dto.DEFAULT_POST_SEARCH_ORDER
import io.ktor.resources.Resource

@Resource("/posts")
class PostResources(
    val query: String? = null,
    val type: String? = null,
    val order: String = DEFAULT_POST_SEARCH_ORDER,
    val cursor: String? = null,
    val size: Int = DEFAULT_POST_SCROLL_SIZE,
) {
    @Resource("{id}")
    class ById(
        val parent: PostResources = PostResources(),
        val id: Long,
    )
}
