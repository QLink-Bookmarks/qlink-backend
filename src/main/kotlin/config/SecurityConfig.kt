package com.qlink.config

data class SecurityConfig(
  val jwtAudience: String,
  val jwtDomain: String,
  val jwtRealm: String,
  val jwtSecret: String,
)
