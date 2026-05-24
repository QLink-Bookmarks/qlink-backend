@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateFolderRequest(
    val name: String,
    val emoji: String? = null,
    val isShared: Boolean? = null,
)
