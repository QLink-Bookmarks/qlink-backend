package com.qlink.link.domain

import com.qlink.common.text.takeKeepingSurrogatePairs

private const val ELLIPSIS = "…"

/**
 * 본문 일치 결과에 보여 줄 짧은 창과 그 안의 강조 범위.
 * [text]는 말줄임을 포함해 [MAX_TEXT_LENGTH] UTF-16 유닛 이하이고, 강조 범위는 [text] 기준 `[start, end)`이며 겹치지 않는다.
 */
class Snippet private constructor(
    val text: String,
    val highlights: List<Highlight>,
) {
    init {
        require(text.length <= MAX_TEXT_LENGTH) { "스니펫은 말줄임 포함 최대 길이를 넘을 수 없다" }
        require(highlights.all { it.start in 0 until it.end && it.end <= text.length }) { "강조 범위는 스니펫 안에 있어야 한다" }
    }

    data class Highlight(
        val start: Int,
        val end: Int,
    )

    companion object {
        /** 일치어 앞에 남기는 문맥 길이(코드포인트). DB가 자르는 창의 시작이다. */
        const val CONTEXT_BEFORE = 15

        /** DB가 잘라 주는 창의 길이(코드포인트). */
        const val SOURCE_MAX_CODE_POINTS = 96

        const val MAX_TEXT_LENGTH = 100
        private const val FULL_BODY_LENGTH = 100
        private const val SHORT_BODY_NUMERATOR = 3
        private const val SHORT_BODY_DENOMINATOR = 10

        /**
         * DB가 자른 창 [source]와 그 앞뒤가 본문에서 잘렸는지([headCut], [tailCut])로 스니펫을 만든다.
         * 본문이 100자 이상이면 [source]를 그대로(일치어 앞 문맥 ≤ [CONTEXT_BEFORE]), 미만이면 본문의 30%(최소 일치어 길이)만큼
         * 일치어를 가운데 둔 창을 쓴다. 말줄임을 붙인 뒤 [MAX_TEXT_LENGTH]를 넘으면 surrogate 쌍을 지켜 자르고 말줄임으로 끝낸다.
         * 강조 범위는 확정한 [text]에서 찾고, 못 찾으면 비어 있다.
         */
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

        /** 일치어 앞에 `(budget - 일치어 길이) / 2`, 나머지를 뒤에 두고 [budget] 유닛만큼 자른다. 창이 [source]보다 짧으면 잘린 쪽에 말줄임을 붙인다. */
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
