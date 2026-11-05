package com.qlink.link.domain

import com.qlink.common.text.toNfc

/**
 * 링크 검색어. 원본을 `trim → NFC → lowercase`로 정규화한 [value]를 가진다.
 * 정규화한 길이가 [MIN_BODY_SEARCH_LENGTH] 이상일 때만 본문까지 찾는다.
 */
class SearchKeyword private constructor(
    val value: String,
) {
    val isBlank: Boolean
        get() = value.isEmpty()

    val includesBody: Boolean
        get() = value.codePointCount(0, value.length) >= MIN_BODY_SEARCH_LENGTH

    companion object {
        const val MIN_BODY_SEARCH_LENGTH = 2

        fun of(raw: String?): SearchKeyword =
            SearchKeyword(
                raw
                    .orEmpty()
                    .trim()
                    .toNfc()
                    .lowercase(),
            )
    }
}
