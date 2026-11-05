package com.qlink.support.fixture

import com.qlink.linkcontent.client.LinkContentExtraction
import com.qlink.linkcontent.client.LinkContentSyncClient

/**
 * 다음 결과를 큐로 받는 가짜 본문 추출 클라이언트.
 * [onInvoke]는 호출 중(클레임과 완료 사이)에 실행되어 URL reset·링크 삭제·stale 회수를 끼워 넣을 수 있다.
 * 큐가 비면 [LinkContentExtraction.Invalid]를 돌려준다.
 */
class FakeLinkContentSyncClient : LinkContentSyncClient {
    private val extractions = ArrayDeque<LinkContentExtraction>()
    var onInvoke: suspend () -> Unit = {}
    val calls = mutableListOf<String>()

    override suspend fun extract(
        url: String,
        traceId: String,
        deadlineMs: Long,
    ): LinkContentExtraction {
        calls += url
        onInvoke()
        return extractions.removeFirstOrNull() ?: LinkContentExtraction.Invalid
    }

    fun enqueue(vararg extraction: LinkContentExtraction) {
        extractions.addAll(extraction)
    }

    fun reset() {
        extractions.clear()
        onInvoke = {}
        calls.clear()
    }
}
