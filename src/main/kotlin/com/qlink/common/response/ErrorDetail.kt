package com.qlink.common.response

import kotlinx.serialization.Serializable

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val cause: String? = null,
    val causeMessage: String? = null,
)
