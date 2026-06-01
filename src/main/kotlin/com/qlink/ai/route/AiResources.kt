package com.qlink.ai.route

import io.ktor.resources.Resource

@Resource("/ai")
class AiResources {
    @Resource("providers/models")
    class ProviderModels(
        val parent: AiResources = AiResources(),
    )
}
