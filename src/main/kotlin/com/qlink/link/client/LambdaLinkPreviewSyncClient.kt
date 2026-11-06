package com.qlink.link.client

import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.sdk.kotlin.services.lambda.model.InvokeRequest
import aws.sdk.kotlin.services.lambda.model.TooManyRequestsException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import com.qlink.config.LinkPreviewConfig
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import java.util.Base64
import java.util.UUID
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

class LambdaLinkPreviewSyncClient(
    private val lambdaClient: LambdaClient,
    private val config: LinkPreviewConfig,
    private val meterRegistry: MeterRegistry,
    private val log: Logger,
) : LinkPreviewSyncClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(
        url: String,
        mode: LinkPreviewMode,
        caller: LinkPreviewCaller,
        faviconBytes: ByteArray?,
    ): LinkPreviewExtraction {
        val traceId = UUID.randomUUID().toString()
        val started = TimeSource.Monotonic.markNow()

        val extraction =
            try {
                invoke(url = url, mode = mode, traceId = traceId, faviconBytes = faviconBytes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: TooManyRequestsException) {
                log.warn("[LINK_PREVIEW_LAMBDA] traceId={} caller={} throttled cause={}", traceId, caller, error::class.simpleName)
                LinkPreviewExtraction.Throttled
            } catch (error: Exception) {
                log.warn("[LINK_PREVIEW_LAMBDA] traceId={} caller={} failed cause={}", traceId, caller, error::class.simpleName)
                LinkPreviewExtraction.Failed
            }
        val duration = started.elapsedNow()

        Timer
            .builder(DURATION_METRIC)
            .tag("caller", caller.tag)
            .tag("outcome", extraction.outcomeTag())
            .register(meterRegistry)
            .record(duration.toJavaDuration())
        log.info(
            "[LINK_PREVIEW_LAMBDA] traceId={} caller={} mode={} outcome={} durationMs={}",
            traceId,
            caller,
            mode,
            extraction.outcomeTag(),
            duration.inWholeMilliseconds,
        )
        return extraction
    }

    private suspend fun invoke(
        url: String,
        mode: LinkPreviewMode,
        traceId: String,
        faviconBytes: ByteArray?,
    ): LinkPreviewExtraction {
        (faviconBytes == null || mode == LinkPreviewMode.FAVICON_ONLY).requireTrue(ErrorCode.LINK_INLINE_FAVICON_MODE_INVALID)
        (faviconBytes == null || faviconBytes.size <= MAX_INLINE_FAVICON_BYTES).requireTrue(ErrorCode.LINK_INLINE_FAVICON_TOO_LARGE)
        val payload =
            json.encodeToString(
                LinkPreviewLambdaRequest(
                    url = url,
                    mode = mode.name,
                    traceId = traceId,
                    favicon = faviconBytes?.let { LinkPreviewLambdaFavicon(base64 = Base64.getEncoder().encodeToString(it)) },
                ),
            )
        (payload.toByteArray().size <= MAX_INVOKE_PAYLOAD_BYTES).requireTrue(ErrorCode.LINK_PREVIEW_PAYLOAD_TOO_LARGE)

        val response =
            lambdaClient.invoke(
                InvokeRequest {
                    functionName = config.functionName
                    this.payload = payload.encodeToByteArray()
                },
            )
        val body = response.payload?.decodeToString()

        return if (response.functionError == null && response.statusCode == 200 && body != null) {
            LinkPreviewExtraction.Success(json.decodeFromString<LinkPreviewLambdaResponse>(body).toResult())
        } else {
            LinkPreviewExtraction.Failed
        }
    }

    private fun LinkPreviewExtraction.outcomeTag(): String =
        when (this) {
            is LinkPreviewExtraction.Success -> "success"
            LinkPreviewExtraction.Failed -> "failed"
            LinkPreviewExtraction.Throttled -> "throttled"
        }

    private companion object {
        const val DURATION_METRIC = "link_preview_lambda_duration"
        const val MAX_INLINE_FAVICON_BYTES = 4 * 1024 * 1024
        const val MAX_INVOKE_PAYLOAD_BYTES = 5_800_000
    }
}
