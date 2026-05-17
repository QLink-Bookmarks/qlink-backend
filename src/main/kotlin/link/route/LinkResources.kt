package com.qlink.link.route

import io.ktor.resources.Resource

@Resource("/links")
class Links {

    @Resource("{id}")
    class ById(
        val parent: Links = Links(),
        val id: Long
    )

}