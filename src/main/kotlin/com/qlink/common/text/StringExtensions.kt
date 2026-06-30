package com.qlink.common.text

private const val ELLIPSIS = "…"

/**
 * 문자열이 [maxLength]를 넘으면 뒤를 잘라 마지막에 […]를 붙인다.
 * 반환 길이는 항상 [maxLength] 이하이며, […] 한 글자를 길이에 포함한다.
 */
fun String.truncate(maxLength: Int): String =
    if (length <= maxLength) {
        this
    } else {
        take((maxLength - ELLIPSIS.length).coerceAtLeast(0)) + ELLIPSIS
    }
