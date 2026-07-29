package com.qlink.ai.route

import io.ktor.resources.Resource

@Resource("/ai")
class AiResources {
    @Resource("providers")
    class Providers(
        val parent: AiResources = AiResources(),
    ) {
        @Resource("models")
        class Models(
            val parent: Providers = Providers(),
            val isMine: Boolean = false,
        )
    }

    @Resource("users/providers")
    class UserProviders(
        val parent: AiResources = AiResources(),
    )
}
