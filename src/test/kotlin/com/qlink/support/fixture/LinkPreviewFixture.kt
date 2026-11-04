package com.qlink.support.fixture

import com.qlink.link.client.LinkPreviewCaller
import com.qlink.link.client.LinkPreviewExtraction
import com.qlink.link.client.LinkPreviewMode
import com.qlink.link.client.LinkPreviewResult
import com.qlink.link.client.LinkPreviewSyncClient
import java.security.MessageDigest

class FakeLinkPreviewSyncClient : LinkPreviewSyncClient {
    var extraction: LinkPreviewExtraction = LinkPreviewExtraction.Failed
    var onExtract: suspend (String) -> Unit = {}
    val calls = mutableListOf<Pair<String, LinkPreviewMode>>()
    val callers = mutableListOf<LinkPreviewCaller>()
    val faviconBytes = mutableListOf<ByteArray?>()

    override suspend fun extract(
        url: String,
        mode: LinkPreviewMode,
        caller: LinkPreviewCaller,
        faviconBytes: ByteArray?,
    ): LinkPreviewExtraction {
        calls += url to mode
        callers += caller
        this.faviconBytes += faviconBytes
        onExtract(url)
        return extraction
    }

    fun respond(result: LinkPreviewResult) {
        extraction = LinkPreviewExtraction.Success(result)
    }

    fun reset() {
        extraction = LinkPreviewExtraction.Failed
        onExtract = {}
        calls.clear()
        callers.clear()
        faviconBytes.clear()
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
