package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.config.LinkPreviewConfig
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewResult
import com.qlink.link.client.LinkPreviewSyncClient
import com.qlink.link.dto.CreateLinkPreviewResponse
import com.qlink.user.repository.UserRepository
import kotlinx.coroutines.CancellationException
import java.net.URI

class CreateLinkPreviewService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val previewClient: LinkPreviewSyncClient,
    private val config: LinkPreviewConfig,
) {
    suspend fun createPreview(
        loginId: Long,
        url: String,
    ): CreateLinkPreviewResponse {
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_PREVIEW_OWNER_NOT_FOUND)
        }
        if (!isAbsoluteHttpUrl(url)) throw BusinessException(ErrorCode.LINK_PREVIEW_URL_INVALID)

        val result =
            try {
                previewClient.extract(url = url, mode = LinkPreviewMode.PREVIEW)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        return createResponse(url = url, result = result)
    }

    private fun createResponse(
        url: String,
        result: LinkPreviewResult?,
    ): CreateLinkPreviewResponse {
        if (result == null) return CreateLinkPreviewResponse(url = url)

        val thumbnailUrl = result.thumbnailKey?.takeIf(::validPreviewKey)?.let(::publicUrl)
        val faviconUrl =
            if (thumbnailUrl == null) {
                result.faviconKey?.takeIf(::validFaviconKey)?.let(::publicUrl)
            } else {
                null
            }

        return CreateLinkPreviewResponse(
            url = url,
            title =
                result.title
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.take(300),
            thumbnailUrl = thumbnailUrl,
            faviconUrl = faviconUrl,
            isVideo = result.isVideo,
        )
    }

    private fun validPreviewKey(key: String): Boolean = key.matches(keyPattern("preview"))

    private fun validFaviconKey(key: String): Boolean = key.matches(keyPattern("favicon"))

    private fun keyPattern(kind: String): Regex = Regex("^${config.environment}-$kind-[0-9a-f]{64}\\.(?:jpg|jpeg|png|gif|webp)$")

    private fun publicUrl(key: String): String = "${config.publicBaseUrl.trimEnd('/')}/$key"

    private fun isAbsoluteHttpUrl(value: String): Boolean =
        runCatching { URI(value) }.getOrNull()?.let {
            it.isAbsolute && it.host != null && it.userInfo == null &&
                (it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true))
        } ?: false
}
