package com.qlink.support.fixture

import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewResult
import com.qlink.link.client.LinkPreviewSyncClient
import java.security.MessageDigest

class FakeLinkPreviewSyncClient : LinkPreviewSyncClient {
    var result: LinkPreviewResult? = null
    var failure: Exception? = null
    val calls = mutableListOf<Pair<String, LinkPreviewMode>>()

    override suspend fun extract(
        url: String,
        mode: LinkPreviewMode,
        faviconBytes: ByteArray?,
    ): LinkPreviewResult? {
        calls += url to mode
        failure?.let { throw it }
        return result
    }

    fun reset() {
        result = null
        failure = null
        calls.clear()
    }
}

object LinkPreviewFixture {
    fun key(
        kind: String,
        environment: String = "dev",
        extension: String = "png",
    ): String {
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(RandomFixture.randomUrl().encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
        return "$environment-$kind-$hash.$extension"
    }
}
