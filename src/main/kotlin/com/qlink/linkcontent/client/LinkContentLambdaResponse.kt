package com.qlink.linkcontent.client

import com.qlink.linkcontent.domain.ContentFailureCode
import kotlinx.serialization.Serializable
import kotlin.time.Instant

private const val OUTCOME_COLLECTED = "COLLECTED"
private const val OUTCOME_BLOCKED = "BLOCKED"
private const val OUTCOME_FAILED = "FAILED"
private const val TRUNCATION_NONE = "NONE"

@Serializable
internal data class LinkContentLambdaResponse(
    val outcome: String,
    val code: String? = null,
    val upstreamStatus: Int? = null,
    val retryAfterSeconds: Long? = null,
    val finalUrl: String? = null,
    val canonicalUrl: String? = null,
    val title: String? = null,
    val siteName: String? = null,
    val author: String? = null,
    val publishedAt: String? = null,
    val language: String? = null,
    val text: String? = null,
    val truncation: String = TRUNCATION_NONE,
) {
    fun toExtraction(): LinkContentExtraction =
        when (outcome) {
            OUTCOME_COLLECTED -> text?.let(::collected) ?: LinkContentExtraction.Invalid
            OUTCOME_BLOCKED -> LinkContentExtraction.Blocked(code = ContentFailureCode.from(code))
            OUTCOME_FAILED -> failed()
            else -> LinkContentExtraction.Invalid
        }

    private fun collected(text: String): LinkContentExtraction =
        LinkContentExtraction.Collected(
            LinkContentResult(
                text = text,
                title = title,
                siteName = siteName,
                author = author,
                publishedAt = publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                canonicalUrl = canonicalUrl,
                finalUrl = finalUrl,
                language = language,
                isTruncated = truncation != TRUNCATION_NONE,
            ),
        )

    private fun failed(): LinkContentExtraction =
        LinkContentExtraction.Failed(
            code = ContentFailureCode.from(code),
            upstreamStatus = upstreamStatus,
            retryAfterSeconds = retryAfterSeconds?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt(),
        )
}
