@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotBlank
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class BookmarkImport(
    val id: Long? = null,
    val ownerId: Long,
    val clientRequestId: Uuid,
    val fileName: String,
    val status: ImportStatus,
    val linkCount: Int,
    val folderCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val reversedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    fun validateOwner(ownerId: Long) {
        if (this.ownerId != ownerId) {
            throw BusinessException(ErrorCode.IMPORT_NOT_FOUND)
        }
    }

    fun progress(finishedThumbnailCount: Int): Double =
        when (linkCount) {
            0 -> 1.0
            else -> (finishedThumbnailCount.toDouble() / linkCount).coerceIn(0.0, 1.0)
        }

    fun isReversible(
        removableLinkCount: Int,
        removableNewFolderCount: Int,
    ): Boolean = removableLinkCount > 0 || removableNewFolderCount > 0

    fun status(isReversible: Boolean): ImportStatus =
        when {
            reversedAt == null -> status
            isReversible -> ImportStatus.PARTIALLY_UNDONE
            else -> ImportStatus.UNDONE
        }

    fun reversalTargets(
        linkIds: List<Long>,
        folderIds: List<Long>,
        folders: List<ImportFolderState>,
        links: List<ImportLinkState>,
    ): ImportReversalTargets {
        (linkIds.isNotEmpty() || folderIds.isNotEmpty()).requireTrue(ErrorCode.IMPORT_REVERSAL_ITEMS_EMPTY)

        val newFolderByFolderId = folders.filter { it.folder.kind == ImportFolderKind.NEW }.associateBy { it.folder.folderId }
        val linkByLinkId = links.associateBy { it.link.linkId }
        val selectedFolders =
            folderIds.distinct().map {
                newFolderByFolderId[it]
                    ?: throw BusinessException(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID)
            }
        val selectedLinks =
            linkIds.distinct().map {
                linkByLinkId[it]
                    ?: throw BusinessException(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID)
            }
        selectedFolders
            .none { it.state == ReversalFolderState.SHARED }
            .requireTrue(ErrorCode.IMPORT_REVERSAL_FOLDER_SHARED)

        return ImportReversalTargets(
            folders = selectedFolders.filter { it.folder.isRemovable(it.state) }.map { it.folder },
            links = selectedLinks.filter { it.link.isRemovable(it.state) }.map { it.link },
        )
    }

    fun reverse(
        targets: ImportReversalTargets,
        now: Instant,
    ): BookmarkImport = if (targets.isEmpty) this else reversed(reversedAt ?: now)

    private fun reversed(at: Instant): BookmarkImport =
        BookmarkImport(
            id = id,
            ownerId = ownerId,
            clientRequestId = clientRequestId,
            fileName = fileName,
            status = status,
            linkCount = linkCount,
            folderCount = folderCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            reversedAt = at,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        const val FILE_NAME_MAX_LENGTH = 255

        fun create(
            ownerId: Long,
            clientRequestId: Uuid,
            fileName: String,
            linkCount: Int,
            folderCount: Int,
            skippedCount: Int,
            failedCount: Int,
        ): BookmarkImport {
            fileName.requireNotBlank(ErrorCode.IMPORT_FILE_NAME_BLANK)
            fileName.requireNotOver(FILE_NAME_MAX_LENGTH, ErrorCode.IMPORT_FILE_NAME_OVER_MAX)

            return BookmarkImport(
                ownerId = ownerId,
                clientRequestId = clientRequestId,
                fileName = fileName,
                status =
                    when {
                        failedCount == 0 -> ImportStatus.COMPLETED
                        linkCount > 0 -> ImportStatus.PARTIALLY_FAILED
                        else -> ImportStatus.FAILED
                    },
                linkCount = linkCount,
                folderCount = folderCount,
                skippedCount = skippedCount,
                failedCount = failedCount,
            )
        }
    }
}
