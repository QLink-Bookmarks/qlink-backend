@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderInvitationResponse(
    val invitation: String,
)

@Serializable
data class AcceptFolderInvitationResponse(
    val folderId: Long,
)
