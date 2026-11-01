package com.qlink.link.client

import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.sdk.kotlin.services.lambda.model.InvokeRequest
import com.qlink.config.LinkPreviewConfig
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID
import kotlin.time.TimeSource

class LambdaLinkPreviewSyncClient(
    private val lambdaClient: LambdaClient,
    private val config: LinkPreviewConfig,
) : LinkPreviewSyncClient {
    private val log = LoggerFactory.getLogger(LambdaLinkPreviewSyncClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(
        url: String,
        mode: LinkPreviewMode,
        faviconBytes: ByteArray?,
    ): LinkPreviewResult? {
        val traceId = UUID.randomUUID().toString()
        val started = TimeSource.Monotonic.markNow()

        return try {
            require(faviconBytes == null || mode == LinkPreviewMode.FAVICON_ONLY)
            require(faviconBytes == null || faviconBytes.size <= MAX_INLINE_FAVICON_BYTES)
            val payload =
                json.encodeToString(
                    LambdaRequest(
                        url = url,
                        mode = mode.name,
                        traceId = traceId,
                        favicon = faviconBytes?.let { LambdaFavicon(base64 = Base64.getEncoder().encodeToString(it)) },
                    ),
                )
            require(payload.toByteArray().size <= MAX_INVOKE_PAYLOAD_BYTES)
            val request =
                InvokeRequest {
                    functionName = config.functionName
                    this.payload = payload.encodeToByteArray()
                }
            val response = lambdaClient.invoke(request)
            val body = response.payload?.decodeToString()
            val result =
                if (response.functionError == null && response.statusCode == 200 && body != null) {
                    json.decodeFromString<LambdaResponse>(body).toResult()
                } else {
                    null
                }

            log.info(
                "[LINK_PREVIEW_LAMBDA] traceId={} mode={} outcome={} durationMs={}",
                traceId,
                mode,
                if (result == null) "FAILED" else "OK",
                started.elapsedNow().inWholeMilliseconds,
            )
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn(
                "[LINK_PREVIEW_LAMBDA] traceId={} mode={} outcome=FAILED durationMs={} cause={}",
                traceId,
                mode,
                started.elapsedNow().inWholeMilliseconds,
                error::class.simpleName,
            )
            null
        }
    }
}

private const val MAX_INLINE_FAVICON_BYTES = 4 * 1024 * 1024
private const val MAX_INVOKE_PAYLOAD_BYTES = 5_800_000

@Serializable
private data class LambdaRequest(
    val url: String,
    val mode: String,
    val traceId: String,
    val favicon: LambdaFavicon? = null,
)

@Serializable
private data class LambdaFavicon(
    val base64: String,
)

@Serializable
private data class LambdaResponse(
    val title: String? = null,
    val thumbnailKey: String? = null,
    val faviconKey: String? = null,
    val isVideo: Boolean = false,
) {
    fun toResult(): LinkPreviewResult =
        LinkPreviewResult(
            title = title,
            thumbnailKey = thumbnailKey,
            faviconKey = faviconKey,
            isVideo = isVideo,
        )
}
