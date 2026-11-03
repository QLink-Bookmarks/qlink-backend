package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.ImportLink
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.domain.service.ImportTrackDomainService
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.common.transaction.TransactionRunner
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.client.LinkPreviewCaller
import com.qlink.link.client.LinkPreviewExtraction
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewResult
import com.qlink.link.client.LinkPreviewSyncClient
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository

class FillImportThumbnailService(
    private val tx: TransactionRunner,
    private val bookmarkImportRepository: BookmarkImportRepository,
    private val linkRepository: LinkRepository,
    private val previewClient: LinkPreviewSyncClient,
    private val urlResolver: PreviewImageUrlResolver,
) {
    suspend fun fill(importLinkId: Long): ThumbnailStatus? {
        val (row, link) = tx.required { claim(importLinkId) } ?: return null
        if (!row.isThumbnailPending || link == null) return row.thumbnailStatus

        return when (
            val extraction =
                previewClient.extract(url = link.url, mode = LinkPreviewMode.PREVIEW, caller = LinkPreviewCaller.IMPORT_THUMBNAIL)
        ) {
            is LinkPreviewExtraction.Success -> tx.required { complete(row = row, preview = extraction.result) }
            LinkPreviewExtraction.Failed -> tx.required { save(row.failThumbnail()) }
            LinkPreviewExtraction.Throttled -> tx.required { save(row.releaseThumbnail()) }
        }
    }

    private suspend fun claim(importLinkId: Long): Claim? {
        if (!bookmarkImportRepository.markThumbnailStarted(importLinkId, ImportLink.THUMBNAIL_STALE_AFTER)) return null
        val row = bookmarkImportRepository.findLinkById(importLinkId) ?: return null
        val link = linkRepository.findById(row.linkId)
        val prepared = ImportTrackDomainService.prepareThumbnail(link = row, current = link)
        if (!prepared.isThumbnailPending) return save(prepared)?.let { Claim(row = prepared, link = link) }

        return Claim(row = prepared, link = link)
    }

    private suspend fun complete(
        row: ImportLink,
        preview: LinkPreviewResult,
    ): ThumbnailStatus? {
        val link = linkRepository.findById(row.linkId)
        val prepared = ImportTrackDomainService.prepareThumbnail(link = row, current = link)
        if (!prepared.isThumbnailPending || link == null) return save(prepared)

        val status = save(prepared.completeThumbnail()) ?: return null
        val thumbnailUrl = preview.thumbnailKey?.let(urlResolver::previewUrl)
        linkRepository.updatePreview(
            link.fillPreview(
                thumbnailUrl = thumbnailUrl,
                faviconUrl = preview.faviconKey?.takeIf { thumbnailUrl == null }?.let(urlResolver::faviconUrl),
                isVideo = preview.isVideo,
            ),
        )
        return status
    }

    private suspend fun save(row: ImportLink): ThumbnailStatus? =
        row.thumbnailStatus.takeIf { bookmarkImportRepository.updateThumbnailResult(row) }

    private data class Claim(
        val row: ImportLink,
        val link: Link?,
    )
}
