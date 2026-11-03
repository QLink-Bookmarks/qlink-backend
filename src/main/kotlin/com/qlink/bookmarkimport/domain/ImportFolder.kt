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

    fun reversalState(facts: ImportReversalFacts): ReversalFolderState =
        when {
            reversedAt != null -> ReversalFolderState.REVERSED
            folderId in facts.deletedFolderIds -> ReversalFolderState.DELETED
            folderId in facts.sharedFolderIds -> ReversalFolderState.SHARED
            else -> ReversalFolderState.ACTIVE
        }

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
