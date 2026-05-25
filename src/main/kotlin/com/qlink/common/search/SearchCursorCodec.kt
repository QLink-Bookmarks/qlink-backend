package com.qlink.common.search

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.Base64CursorCodec

object SearchCursorCodec {
    inline fun <reified T> decode(
        encodedCursor: String,
        expectedOrder: SearchOrder,
        validate: (T, SearchOrder) -> Unit,
    ): SearchCursor<T> {
        val decoded = Base64CursorCodec.decode<SearchCursor<T>>(encodedCursor)

        if (decoded.order != expectedOrder) {
            throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
        }

        validate(decoded.value, expectedOrder)

        return decoded
    }

    inline fun <reified T> encode(
        order: SearchOrder,
        value: T,
    ): String = Base64CursorCodec.encode(SearchCursor(order = order, value = value))
}
