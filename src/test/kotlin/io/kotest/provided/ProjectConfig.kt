package io.kotest.provided

import com.qlink.support.ServiceTestEnvironment
import io.kotest.core.config.AbstractProjectConfig

object ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        ServiceTestEnvironment.start()
    }

    override suspend fun afterProject() {
        ServiceTestEnvironment.stop()
    }
}
