package com.qlink.bookmarkimport.domain

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
    fun displayName(currentName: String?): String = currentName ?: name

    fun reversalState(
        isDeleted: Boolean,
        isShared: Boolean,
    ): ReversalFolderState =
        when {
            reversedAt != null -> ReversalFolderState.REVERSED
            isDeleted -> ReversalFolderState.DELETED
            isShared -> ReversalFolderState.SHARED
            else -> ReversalFolderState.ACTIVE
        }

    fun withState(
        isDeleted: Boolean,
        isShared: Boolean,
    ): ImportFolderState = ImportFolderState(folder = this, isDeleted = isDeleted, isShared = isShared)

    fun isRemovable(state: ReversalFolderState): Boolean = kind == ImportFolderKind.NEW && state == ReversalFolderState.ACTIVE

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
