package com.qlink.link.service

import com.qlink.common.transaction.TransactionRunner
import com.qlink.image.service.PreviewImageUrlResolver
import com.qlink.link.client.LinkPreviewCaller
import com.qlink.link.client.LinkPreviewExtraction
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewSyncClient
import com.qlink.link.repository.LinkRepository

class FillLinkPreviewService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val previewClient: LinkPreviewSyncClient,
    private val urlResolver: PreviewImageUrlResolver,
) {
    suspend fun fill(linkId: Long) {
        val link = tx.readOnly { linkRepository.findById(linkId) } ?: return
        if (link.hasPreview) return

        val extraction = previewClient.extract(url = link.url, mode = LinkPreviewMode.PREVIEW, caller = LinkPreviewCaller.LINK_FILL)
        val preview = (extraction as? LinkPreviewExtraction.Success)?.result ?: return
        val thumbnailUrl = preview.thumbnailKey?.let(urlResolver::previewUrl)
        val faviconUrl = preview.faviconKey?.takeIf { thumbnailUrl == null }?.let(urlResolver::faviconUrl)
        if (thumbnailUrl == null && faviconUrl == null) return

        tx.required {
            linkRepository.updatePreview(
                link.fillPreview(
                    thumbnailUrl = thumbnailUrl,
                    faviconUrl = faviconUrl,
                    isVideo = preview.isVideo,
                ),
            )
        }
    }
}
