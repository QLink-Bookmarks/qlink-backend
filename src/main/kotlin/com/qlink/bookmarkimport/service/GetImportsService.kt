package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.dto.ImportCursorValue
import com.qlink.bookmarkimport.dto.ImportItemCountsQuery
import com.qlink.bookmarkimport.dto.ImportSummary
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.scroll.Base64CursorCodec
import com.qlink.common.scroll.DEFAULT_SCROLL_SIZE
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.user.repository.UserRepository

class GetImportsService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val bookmarkImportRepository: BookmarkImportRepository,
) {
    suspend fun getImports(
        loginId: Long,
        scrollRequest: ScrollRequest,
    ): ScrollResponse<ImportSummary> =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.IMPORT_OWNER_NOT_FOUND)

            val cursor = scrollRequest.cursor?.let(::decodeCursor)
            val size = scrollRequest.size.takeIf { it > 0 } ?: DEFAULT_SCROLL_SIZE
            val imports = bookmarkImportRepository.search(ownerId = loginId, cursor = cursor, size = size)
            val hasNext = imports.size > size
            val contents = imports.take(size)
            val countsByImportId = bookmarkImportRepository.countItems(contents.map { it.id!! }).associateBy { it.importId }

            ScrollResponse(
                isEmpty = contents.isEmpty(),
                contents = contents.map { it.toSummary(countsByImportId.getValue(it.id!!)) },
                nextCursor = contents.lastOrNull()?.takeIf { hasNext }?.let(::encodeCursor),
                hasNext = hasNext,
            )
        }

    private fun decodeCursor(encoded: String): ImportCursorValue =
        Base64CursorCodec.decode<ImportCursorValue>(encoded).also {
            it.createdAt ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
            it.id ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
        }

    private fun encodeCursor(bookmarkImport: BookmarkImport): String =
        Base64CursorCodec.encode(ImportCursorValue(createdAt = bookmarkImport.createdAt, id = bookmarkImport.id))

    private fun BookmarkImport.toSummary(counts: ImportItemCountsQuery): ImportSummary =
        ImportSummary.of(
            bookmarkImport = this,
            state =
                summaryState(
                    finishedThumbnailCount = counts.finishedThumbnailCount,
                    removableLinkCount = counts.removableLinkCount,
                    removableNewFolderCount = counts.removableNewFolderCount,
                ),
        )
}
