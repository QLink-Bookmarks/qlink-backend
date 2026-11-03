package com.qlink.bookmarkimport.domain

class ImportPlanFailure(
    val title: String,
    val url: String,
    val reason: ImportFailureReason,
)
