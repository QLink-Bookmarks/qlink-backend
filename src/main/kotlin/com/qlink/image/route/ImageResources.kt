package com.qlink.image.route

import io.ktor.resources.Resource

@Resource("/images")
class ImageResources(
    val purpose: String? = null,
    val url: String? = null,
)
