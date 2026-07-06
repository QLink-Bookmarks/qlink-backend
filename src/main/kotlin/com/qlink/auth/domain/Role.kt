package com.qlink.auth.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    SUPER_ADMIN,
    ADMIN,
    NORMAL,
    GUEST,
    ;

    val isAdmin: Boolean
        get() = this == SUPER_ADMIN || this == ADMIN
}
