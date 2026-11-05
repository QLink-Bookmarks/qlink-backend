package com.qlink.link.domain

import com.qlink.common.text.toNfc

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
