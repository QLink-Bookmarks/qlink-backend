@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateLinkResponse(
    val id: Long,
)
