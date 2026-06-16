package com.qlink.common.scroll

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import kotlinx.serialization.json.Json
import java.util.Base64

object Base64CursorCodec {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    inline fun <reified T> decode(encodedValue: String): T =
        runCatching {
            val payload = Base64.getDecoder().decode(encodedValue)
            json.decodeFromString<T>(payload.decodeToString())
        }.getOrElse {
            throw BusinessException(ErrorCode.COMMON_CURSOR_MALFORMED, it)
        }

    inline fun <reified T> encode(value: T): String = Base64.getEncoder().encodeToString(json.encodeToString(value).encodeToByteArray())
}
