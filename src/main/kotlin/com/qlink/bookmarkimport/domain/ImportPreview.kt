package com.qlink.bookmarkimport.domain

class ImportPreview(
    val folders: List<ImportPreviewFolder>,
    val links: List<ImportPreviewLink>,
)

class ImportPreviewFolder(
    val key: String,
    val matchType: ImportFolderMatchType,
    val folderId: Long?,
    val suggestedName: String,
)

class ImportPreviewLink(
    val key: String,
    val status: ImportPreviewLinkStatus,
)
