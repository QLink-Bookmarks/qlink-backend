@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderResponse(
    val id: Long,
)

@Serializable
data class UpdateFolderResponse(
    val id: Long,
)
