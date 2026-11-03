package com.qlink.bookmarkimport.domain

class ImportReversalTargets(
    val folderIds: List<Long>,
    val linkIds: List<Long>,
) {
    val isEmpty: Boolean
        get() = folderIds.isEmpty() && linkIds.isEmpty()
}
