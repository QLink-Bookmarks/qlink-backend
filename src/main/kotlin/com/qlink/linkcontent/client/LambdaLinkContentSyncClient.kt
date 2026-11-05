package com.qlink.linkcontent.client

import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.sdk.kotlin.services.lambda.model.InvokeRequest
import aws.sdk.kotlin.services.lambda.model.TooManyRequestsException
import com.qlink.config.LinkContentConfig
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

class LambdaLinkContentSyncClient(
    private val lambdaClient: LambdaClient,
    private val config: LinkContentConfig,
    private val meterRegistry: MeterRegistry,
) : LinkContentSyncClient {
    private val log = LoggerFactory.getLogger(LambdaLinkContentSyncClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(
        url: String,
        traceId: String,
        deadlineMs: Long,
    ): LinkContentExtraction {
        val started = TimeSource.Monotonic.markNow()

        val extraction =
            try {
                withTimeoutOrNull(config.invokeTimeout) { invoke(url = url, traceId = traceId, deadlineMs = deadlineMs) }
                    ?: LinkContentExtraction.Invalid.also {
                        log.warn("[LINK_CONTENT_LAMBDA] traceId={} invoke timed out", traceId)
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TooManyRequestsException) {
                log.warn("[LINK_CONTENT_LAMBDA] traceId={} throttled cause={}", traceId, error::class.simpleName)
                LinkContentExtraction.Throttled
            } catch (error: Exception) {
                log.warn("[LINK_CONTENT_LAMBDA] traceId={} failed cause={}", traceId, error::class.simpleName)
                LinkContentExtraction.Invalid
            }
        val duration = started.elapsedNow()

        Timer
            .builder(DURATION_METRIC)
            .tag("outcome", extraction.outcomeTag())
            .register(meterRegistry)
            .record(duration.toJavaDuration())
        extraction.failureCode()?.let { code ->
            Counter
                .builder(FAILURE_METRIC)
                .tag("code", code)
                .register(meterRegistry)
                .increment()
        }
        log.info(
            "[LINK_CONTENT_LAMBDA] traceId={} outcome={} code={} durationMs={}",
            traceId,
            extraction.outcomeTag(),
            extraction.failureCode(),
            duration.inWholeMilliseconds,
        )
        return extraction
    }

    private suspend fun invoke(
        url: String,
        traceId: String,
        deadlineMs: Long,
    ): LinkContentExtraction {
        val payload = json.encodeToString(LinkContentLambdaRequest(url = url, traceId = traceId, deadlineMs = deadlineMs))
        val response =
            lambdaClient.invoke(
                InvokeRequest {
                    functionName = config.functionName
                    this.payload = payload.encodeToByteArray()
                },
            )
        val body = response.payload

        return if (response.functionError == null && response.statusCode == 200 && body != null && body.size <= MAX_RESPONSE_BYTES) {
            json.decodeFromString<LinkContentLambdaResponse>(body.decodeToString()).toExtraction()
        } else {
            log.warn(
                "[LINK_CONTENT_LAMBDA] traceId={} invalid response status={} functionError={} bytes={}",
                traceId,
                response.statusCode,
                response.functionError,
                body?.size,
            )
            LinkContentExtraction.Invalid
        }
    }

    private fun LinkContentExtraction.outcomeTag(): String =
        when (this) {
            is LinkContentExtraction.Collected -> "collected"
            is LinkContentExtraction.Failed -> "failed"
            is LinkContentExtraction.Blocked -> "blocked"
            LinkContentExtraction.Throttled -> "throttled"
            LinkContentExtraction.Invalid -> "invalid"
        }

    private fun LinkContentExtraction.failureCode(): String? =
        when (this) {
            is LinkContentExtraction.Failed -> code.name
            is LinkContentExtraction.Blocked -> code.name
            else -> null
        }

    private companion object {
        const val DURATION_METRIC = "link_content_lambda_duration"
        const val FAILURE_METRIC = "link_content_failures"
        const val MAX_RESPONSE_BYTES = 1_000_000
    }
}
