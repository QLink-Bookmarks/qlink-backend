package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.transaction.TransactionRunner

class CountImportThumbnailTargetsService(
    private val tx: TransactionRunner,
    private val bookmarkImportRepository: BookmarkImportRepository,
) {
    suspend fun count(): Long =
        tx.readOnly {
            bookmarkImportRepository.countThumbnailTargets(staleAfter = ImportLink.THUMBNAIL_STALE_AFTER)
        }
}
