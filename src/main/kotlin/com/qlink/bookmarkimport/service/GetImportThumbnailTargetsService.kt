package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.dto.ImportThumbnailTargetQuery
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.transaction.TransactionRunner

class GetImportThumbnailTargetsService(
    private val tx: TransactionRunner,
    private val bookmarkImportRepository: BookmarkImportRepository,
) {
    suspend fun getTargets(size: Int): List<ImportThumbnailTargetQuery> =
        tx.readOnly {
            bookmarkImportRepository.findThumbnailTargets(staleAfter = ImportLink.THUMBNAIL_STALE_AFTER, size = size)
        }
}
