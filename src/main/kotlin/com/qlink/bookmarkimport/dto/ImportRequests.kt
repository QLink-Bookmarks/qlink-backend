@file:Suppress("ktlint:standard:filename")
@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class CreateImportPreviewRequest(
    val folders: List<CreateImportPreviewFolderRequest>,
    val links: List<CreateImportPreviewLinkRequest>,
)

@Serializable
data class CreateImportPreviewFolderRequest(
    val key: String,
    val name: String,
)

@Serializable
data class CreateImportPreviewLinkRequest(
    val key: String,
    val url: String,
)

@Serializable
data class CreateImportRequest(
    val clientRequestId: Uuid,
    val fileName: String,
    val folders: List<CreateImportFolderRequest>,
    val links: List<CreateImportLinkRequest>,
)

@Serializable
data class CreateImportFolderRequest(
    val key: String,
    val folderId: Long? = null,
    val name: String,
    val createdAt: Instant? = null,
)

@Serializable
data class CreateImportLinkRequest(
    val key: String,
    val url: String,
    val title: String,
    val folderKey: String? = null,
    val createdAt: Instant? = null,
)

@Serializable
data class CreateImportReversalRequest(
    val linkIds: List<Long>,
    val folderIds: List<Long>,
)
