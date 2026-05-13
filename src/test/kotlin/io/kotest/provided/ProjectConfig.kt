package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import support.ServiceTestEnvironment

object ProjectConfig : AbstractProjectConfig() {
  override suspend fun beforeProject() {
    ServiceTestEnvironment.start()
  }

  override suspend fun afterProject() {
    ServiceTestEnvironment.stop()
  }
}
