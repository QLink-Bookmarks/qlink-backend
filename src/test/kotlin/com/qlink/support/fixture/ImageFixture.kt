package com.qlink.support.fixture

import java.util.Base64

object ImageFixture {
    // 1x1 transparent PNG.
    private const val PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

    fun validPng(): ByteArray = Base64.getDecoder().decode(PNG_BASE64)

    fun notAnImage(): ByteArray = "this is definitely not an image".toByteArray()
}
