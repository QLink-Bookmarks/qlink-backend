package com.qlink.config

data class DataSourceConfig(
  val jdbcUrl: String,
  val username: String,
  val password: String,
  val driverClassName: String,
)
