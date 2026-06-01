package com.qlink.user.route

import io.ktor.resources.Resource

@Resource("/users")
class UserResources {
    @Resource("me")
    class Me(
        val parent: UserResources = UserResources(),
    )
}
