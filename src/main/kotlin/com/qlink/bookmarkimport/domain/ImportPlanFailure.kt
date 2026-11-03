package com.qlink.bookmarkimport.domain

class ImportPlanFailure(
    val title: String,
    val url: String,
    val reason: ImportFailureReason,
) {
    fun toImportFailure(importId: Long): ImportFailure =
        ImportFailure(
            importId = importId,
            title = title,
            url = url,
            reason = reason,
        )
}
