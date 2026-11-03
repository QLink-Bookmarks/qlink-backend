package com.qlink.bookmarkimport.domain

class ImportSummaryState(
    val status: ImportStatus,
    val progress: Double,
    val isReversible: Boolean,
)
