package com.qlink.bookmarkimport.domain

import com.qlink.folder.domain.Folder
import kotlin.time.Instant

class ImportFolder(
    val id: Long? = null,
    val importId: Long,
    val folderId: Long,
    val kind: ImportFolderKind,
    val name: String,
    val reversedAt: Instant? = null,
    val createdAt: Instant? = null,
) {
    fun displayName(current: Folder?): String = current?.name ?: name

    fun reversalState(current: Folder?): ReversalFolderState =
        when {
            reversedAt != null -> ReversalFolderState.REVERSED
            current == null -> ReversalFolderState.DELETED
            current.isShared -> ReversalFolderState.SHARED
            else -> ReversalFolderState.ACTIVE
        }

    fun isRemovable(current: Folder?): Boolean = kind == ImportFolderKind.NEW && reversalState(current) == ReversalFolderState.ACTIVE

    fun reverse(now: Instant): ImportFolder =
        ImportFolder(
            id = id,
            importId = importId,
            folderId = folderId,
            kind = kind,
            name = name,
            reversedAt = now,
            createdAt = createdAt,
        )

    companion object {
        fun create(
            importId: Long,
            folderId: Long,
            kind: ImportFolderKind,
            name: String,
        ): ImportFolder =
            ImportFolder(
                importId = importId,
                folderId = folderId,
                kind = kind,
                name = name,
            )
    }
}
