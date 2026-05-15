package com.qlink.auth.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    GUEST,
    NORMAL,
    ADMIN,
}
