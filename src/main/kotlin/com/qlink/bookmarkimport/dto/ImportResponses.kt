@file:Suppress("ktlint:standard:filename")

package com.qlink.bookmarkimport.dto

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailureReason
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportFolderMatchType
import com.qlink.bookmarkimport.domain.ImportPreviewLinkStatus
import com.qlink.bookmarkimport.domain.ImportStatus
import com.qlink.bookmarkimport.domain.ImportSummaryState
import com.qlink.bookmarkimport.domain.ReversalFolderState
import com.qlink.bookmarkimport.domain.ReversalLinkState
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateImportPreviewResponse(
    val folders: List<CreateImportPreviewFolderResponse>,
    val links: List<CreateImportPreviewLinkResponse>,
)

@Serializable
data class CreateImportPreviewFolderResponse(
    val key: String,
    val matchType: ImportFolderMatchType,
    val folderId: Long?,
    val suggestedName: String,
)

@Serializable
data class CreateImportPreviewLinkResponse(
    val key: String,
    val status: ImportPreviewLinkStatus,
)

@Serializable
data class ImportSummary(
    val id: Long,
    val fileName: String,
    val status: ImportStatus,
    val linkCount: Int,
    val folderCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val progress: Double,
    val isReversible: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun of(
            bookmarkImport: BookmarkImport,
            state: ImportSummaryState,
        ): ImportSummary =
            ImportSummary(
                id = bookmarkImport.id!!,
                fileName = bookmarkImport.fileName,
                status = state.status,
                linkCount = bookmarkImport.linkCount,
                folderCount = bookmarkImport.folderCount,
                skippedCount = bookmarkImport.skippedCount,
                failedCount = bookmarkImport.failedCount,
                progress = state.progress,
                isReversible = state.isReversible,
                createdAt = bookmarkImport.createdAt!!,
            )
    }
}

@Serializable
data class GetImportDetailResponse(
    val id: Long,
    val fileName: String,
    val status: ImportStatus,
    val linkCount: Int,
    val folderCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val progress: Double,
    val isReversible: Boolean,
    val createdAt: Instant,
    val folders: List<GetImportDetailFolderResponse>,
    val failures: List<GetImportDetailFailureResponse>,
) {
    companion object {
        fun of(
            summary: ImportSummary,
            folders: List<GetImportDetailFolderResponse>,
            failures: List<GetImportDetailFailureResponse>,
        ): GetImportDetailResponse =
            GetImportDetailResponse(
                id = summary.id,
                fileName = summary.fileName,
                status = summary.status,
                linkCount = summary.linkCount,
                folderCount = summary.folderCount,
                skippedCount = summary.skippedCount,
                failedCount = summary.failedCount,
                progress = summary.progress,
                isReversible = summary.isReversible,
                createdAt = summary.createdAt,
                folders = folders,
                failures = failures,
            )
    }
}

@Serializable
data class GetImportDetailFolderResponse(
    val kind: ImportFolderKind,
    val folderId: Long?,
    val name: String?,
    val emoji: String?,
    val links: List<GetImportDetailLinkResponse>,
)

@Serializable
data class GetImportDetailLinkResponse(
    val linkId: Long,
    val title: String,
    val url: String,
)

@Serializable
data class GetImportDetailFailureResponse(
    val title: String,
    val url: String,
    val reason: ImportFailureReason,
)

@Serializable
data class GetImportReversalPreviewResponse(
    val folders: List<GetImportReversalPreviewFolderResponse>,
)

@Serializable
data class GetImportReversalPreviewFolderResponse(
    val kind: ImportFolderKind,
    val state: ReversalFolderState,
    val folderId: Long?,
    val name: String?,
    val emoji: String?,
    val links: List<GetImportReversalPreviewLinkResponse>,
)

@Serializable
data class GetImportReversalPreviewLinkResponse(
    val linkId: Long,
    val title: String,
    val url: String,
    val state: ReversalLinkState,
)
