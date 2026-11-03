package com.qlink.bookmarkimport.domain

class ImportReversalFacts(
    val deletedFolderIds: Set<Long>,
    val sharedFolderIds: Set<Long>,
    val deletedLinkIds: Set<Long>,
    val editedLinkIds: Set<Long>,
)
