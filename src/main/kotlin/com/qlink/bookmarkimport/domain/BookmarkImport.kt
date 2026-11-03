@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireNotBlank
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import com.qlink.folder.domain.Folder
import com.qlink.link.domain.Link
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

    fun reversalPreview(
        folders: List<ImportFolder>,
        links: List<ImportLink>,
        currentLinks: List<Link>,
        currentFolders: List<Folder>,
    ): ImportReversalPreview {
        val currentLinkById = currentLinks.associateBy { it.id }
        val currentFolderById = currentFolders.associateBy { it.id }
        val importFolderById = folders.associateBy { it.id }
        val importFolderByFolderId = folders.associateBy { it.folderId }
        val importedLinkIds = links.mapTo(HashSet()) { it.linkId }
        val importedLinks =
            links.map { row ->
                val current = currentLinkById[row.linkId]
                val folderId = if (current != null) current.folderId else row.importFolderId?.let { importFolderById[it]?.folderId }

                folderId to
                    ReversalPreviewLink(
                        linkId = row.linkId,
                        title = current?.title ?: row.title,
                        url = current?.url ?: row.url,
                        state = row.reversalState(current),
                    )
            }
        val addedLaterLinks =
            currentLinks
                .filter { it.id !in importedLinkIds && importFolderByFolderId[it.folderId]?.kind == ImportFolderKind.NEW }
                .map {
                    it.folderId to
                        ReversalPreviewLink(linkId = it.id!!, title = it.title, url = it.url, state = ReversalLinkState.ADDED_LATER)
                }
        val linksByFolderId = (importedLinks + addedLaterLinks).groupBy({ it.first }, { it.second })
        val importGroups =
            folders.map { row ->
                val current = currentFolderById[row.folderId]

                ReversalPreviewFolder(
                    kind = row.kind,
                    state = row.reversalState(current),
                    folderId = row.folderId,
                    name = row.displayName(current),
                    emoji = current?.emoji,
                    links = linksByFolderId[row.folderId].orEmpty(),
                )
            }
        val otherGroups =
            linksByFolderId
                .filterKeys { it != null && it !in importFolderByFolderId }
                .map { (folderId, groupLinks) ->
                    val current = currentFolderById[folderId]

                    ReversalPreviewFolder(
                        kind = ImportFolderKind.EXISTING,
                        state = ReversalFolderState.ACTIVE,
                        folderId = folderId,
                        name = current?.name,
                        emoji = current?.emoji,
                        links = groupLinks,
                    )
                }
        val unfiledGroup =
            linksByFolderId[null]?.let { groupLinks ->
                ReversalPreviewFolder(
                    kind = ImportFolderKind.UNFILED,
                    state = ReversalFolderState.ACTIVE,
                    folderId = null,
                    name = null,
                    emoji = null,
                    links = groupLinks,
                )
            }

        return ImportReversalPreview(folders = importGroups + otherGroups + listOfNotNull(unfiledGroup))
    }

    fun reverse(
        linkIds: List<Long>,
        folderIds: List<Long>,
        folders: List<ImportFolder>,
        links: List<ImportLink>,
        currentLinks: List<Link>,
        currentFolders: List<Folder>,
        now: Instant,
    ): ImportReversal {
        (linkIds.isNotEmpty() || folderIds.isNotEmpty()).requireTrue(ErrorCode.IMPORT_REVERSAL_ITEMS_EMPTY)

        val newFolderByFolderId = folders.filter { it.kind == ImportFolderKind.NEW }.associateBy { it.folderId }
        val importLinkByLinkId = links.associateBy { it.linkId }
        val selectedFolders =
            folderIds.distinct().map {
                newFolderByFolderId[it]
                    ?: throw BusinessException(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID)
            }
        val selectedLinks =
            linkIds.distinct().map {
                importLinkByLinkId[it]
                    ?: throw BusinessException(ErrorCode.IMPORT_REVERSAL_ITEM_INVALID)
            }
        val currentFolderById = currentFolders.associateBy { it.id }
        val currentLinkById = currentLinks.associateBy { it.id }
        val removableFolders =
            selectedFolders.filter { row ->
                val state = row.reversalState(currentFolderById[row.folderId])
                (state != ReversalFolderState.SHARED).requireTrue(ErrorCode.IMPORT_REVERSAL_FOLDER_SHARED)
                state == ReversalFolderState.ACTIVE
            }
        val removableLinks = selectedLinks.filter { it.isRemovable(currentLinkById[it.linkId]) }
        val reversesAny = removableFolders.isNotEmpty() || removableLinks.isNotEmpty()

        return ImportReversal(
            bookmarkImport = if (reversesAny) reversed(reversedAt ?: now) else this,
            linkIds = removableLinks.map { it.linkId },
            folderIds = removableFolders.map { it.folderId },
            importLinks = removableLinks.map { it.reverse(now) },
            importFolders = removableFolders.map { it.reverse(now) },
        )
    }

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

class ImportReversalPreview(
    val folders: List<ReversalPreviewFolder>,
)

class ReversalPreviewFolder(
    val kind: ImportFolderKind,
    val state: ReversalFolderState,
    val folderId: Long?,
    val name: String?,
    val emoji: String?,
    val links: List<ReversalPreviewLink>,
)

class ReversalPreviewLink(
    val linkId: Long,
    val title: String,
    val url: String,
    val state: ReversalLinkState,
)

class ImportReversal(
    val bookmarkImport: BookmarkImport,
    val linkIds: List<Long>,
    val folderIds: List<Long>,
    val importLinks: List<ImportLink>,
    val importFolders: List<ImportFolder>,
)
