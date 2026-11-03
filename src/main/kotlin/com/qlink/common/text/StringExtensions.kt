package com.qlink.common.text

import java.text.Normalizer

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

fun String.takeKeepingSurrogatePairs(maxLength: Int): String =
    take(maxLength).let { head -> if (head.lastOrNull()?.isHighSurrogate() == true) head.dropLast(1) else head }

fun String.toNfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)
