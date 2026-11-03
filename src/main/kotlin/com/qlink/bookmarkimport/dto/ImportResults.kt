@file:Suppress("ktlint:standard:filename")

package com.qlink.bookmarkimport.dto

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFolder
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportFolderMatchType
import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ImportPreviewLinkStatus
import com.qlink.bookmarkimport.domain.ReversalFolderState
import com.qlink.bookmarkimport.domain.ReversalLinkState

data class ImportPreview(
    val folders: List<ImportPreviewFolder>,
    val links: List<ImportPreviewLink>,
)

data class ImportPreviewFolder(
    val key: String,
    val matchType: ImportFolderMatchType,
    val folderId: Long?,
    val suggestedName: String,
)

data class ImportPreviewLink(
    val key: String,
    val status: ImportPreviewLinkStatus,
)

data class ImportReversalPreview(
    val folders: List<ReversalPreviewFolder>,
)

data class ReversalPreviewFolder(
    val kind: ImportFolderKind,
    val state: ReversalFolderState,
    val folderId: Long?,
    val name: String?,
    val emoji: String?,
    val links: List<ReversalPreviewLink>,
)

data class ReversalPreviewLink(
    val linkId: Long,
    val title: String,
    val url: String,
    val state: ReversalLinkState,
)

data class ImportReversal(
    val bookmarkImport: BookmarkImport,
    val linkIds: List<Long>,
    val folderIds: List<Long>,
    val importLinks: List<ImportLink>,
    val importFolders: List<ImportFolder>,
)
