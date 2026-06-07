@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderInvitationRequest(
    val durationDays: Int? = null,
)

@Serializable
data class AcceptFolderInvitationRequest(
    val invitation: String,
)
