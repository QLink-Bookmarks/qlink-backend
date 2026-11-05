@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.linkcontent.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ClaimFence(
    val id: Long,
    val generation: Int,
    val claimToken: Uuid,
)
