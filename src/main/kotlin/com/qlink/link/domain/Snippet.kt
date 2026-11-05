package com.qlink.link.domain

import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireTrue
import com.qlink.common.text.takeKeepingSurrogatePairs

private const val ELLIPSIS = "…"

class Snippet private constructor(
    val text: String,
    val highlights: List<Highlight>,
) {
    init {
        (text.length <= MAX_TEXT_LENGTH).requireTrue(ErrorCode.LINK_SNIPPET_OVER_MAX)
        highlights
            .all { it.start in 0 until it.end && it.end <= text.length }
            .requireTrue(ErrorCode.LINK_SNIPPET_HIGHLIGHTS_INVALID)
    }

    data class Highlight(
        val start: Int,
        val end: Int,
    )

    companion object {
        const val CONTEXT_BEFORE = 15

        const val SOURCE_MAX_CODE_POINTS = 96

        const val MAX_TEXT_LENGTH = 100
        private const val FULL_BODY_LENGTH = 100
        private const val SHORT_BODY_NUMERATOR = 3
        private const val SHORT_BODY_DENOMINATOR = 10

        fun of(
            source: String,
            headCut: Boolean,
            tailCut: Boolean,
            keyword: String,
            bodyLength: Int,
        ): Snippet {
            val windowed =
                if (bodyLength >= FULL_BODY_LENGTH) {
                    withEllipses(text = source, headCut = headCut, tailCut = tailCut)
                } else {
                    centeredWindow(
                        source = source,
                        headCut = headCut,
                        tailCut = tailCut,
                        keyword = keyword,
                        budget = maxOf(keyword.length, bodyLength * SHORT_BODY_NUMERATOR / SHORT_BODY_DENOMINATOR),
                    )
                }
            val text =
                if (windowed.length <= MAX_TEXT_LENGTH) {
                    windowed
                } else {
                    windowed.takeKeepingSurrogatePairs(MAX_TEXT_LENGTH - ELLIPSIS.length) + ELLIPSIS
                }

            return Snippet(text = text, highlights = highlightsOf(text = text, keyword = keyword))
        }

        private fun centeredWindow(
            source: String,
            headCut: Boolean,
            tailCut: Boolean,
            keyword: String,
            budget: Int,
        ): String {
            val lowered = source.lowercase()
            val matchIndex = if (lowered.length == source.length) lowered.indexOf(keyword) else -1
            val preferredStart = (matchIndex - (budget - keyword.length) / 2).coerceAtLeast(0)
            val rawEnd = (preferredStart + budget).coerceAtMost(source.length)
            val rawStart = (rawEnd - budget).coerceAtLeast(0)
            val start = if (rawStart > 0 && source[rawStart].isLowSurrogate()) rawStart + 1 else rawStart
            val end = if (rawEnd in (start + 1) until source.length && source[rawEnd - 1].isHighSurrogate()) rawEnd - 1 else rawEnd

            return withEllipses(
                text = source.substring(start, end),
                headCut = headCut || start > 0,
                tailCut = tailCut || end < source.length,
            )
        }

        private fun withEllipses(
            text: String,
            headCut: Boolean,
            tailCut: Boolean,
        ): String = (if (headCut) ELLIPSIS else "") + text + (if (tailCut) ELLIPSIS else "")

        private fun highlightsOf(
            text: String,
            keyword: String,
        ): List<Highlight> {
            val lowered = text.lowercase()
            if (keyword.isEmpty() || lowered.length != text.length) {
                return emptyList()
            }

            return Regex
                .fromLiteral(keyword)
                .findAll(lowered)
                .map { Highlight(start = it.range.first, end = it.range.last + 1) }
                .toList()
        }
    }
}
