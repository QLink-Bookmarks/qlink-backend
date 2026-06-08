package com.qlink.auth.route

import io.ktor.resources.Resource

@Resource("/auth")
class AuthResources {
    @Resource("sign")
    class Sign(
        val parent: AuthResources = AuthResources(),
    )
}
