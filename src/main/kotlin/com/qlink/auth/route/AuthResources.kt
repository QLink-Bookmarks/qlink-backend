package com.qlink.auth.route

import io.ktor.resources.Resource

@Resource("/auth")
class AuthResources {
    @Resource("sign")
    class Sign(
        val parent: AuthResources = AuthResources(),
    )

    @Resource("signout")
    class Signout(
        val parent: AuthResources = AuthResources(),
    )

    @Resource("token")
    class Token(
        val parent: AuthResources = AuthResources(),
    ) {
        @Resource("refresh")
        class Refresh(
            val parent: Token = Token(),
        ) {
            @Resource("web")
            class Web(
                val parent: Refresh = Refresh(),
            )

            @Resource("native")
            class Native(
                val parent: Refresh = Refresh(),
            )
        }
    }
}
