package com.qlink.linkcontent.repository

import com.qlink.linkcontent.domain.ClaimFence
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.dto.DueKeyset
import com.qlink.linkcontent.dto.LinkContentTargetQuery
import kotlin.time.Duration
import kotlin.time.Instant

interface LinkContentRepository {
    /**
     * 링크마다 1행을 넣는다. 충돌하는 행은 건너뛴다(대상 없는 `ON CONFLICT DO NOTHING`).
     * 이 테이블에서 충돌할 수 있는 제약은 `link_contents_link_id_unique`뿐이므로 이미 행이 있는 링크가 건너뛰어진다.
     */
    suspend fun insertAll(contents: List<LinkContent>)

    suspend fun findById(contentId: Long): LinkContent?

    suspend fun findByLinkId(linkId: Long): LinkContent?

    /** URL 변경 reset. 행이 있으면 generation + 1로 같은 행을 초기화하고, 없으면 새 행을 넣는다. */
    suspend fun upsertForLink(content: LinkContent): LinkContent

    /** PENDING·due·미실행(또는 stale) 행이면 새 claim token으로 클레임해 돌려준다. */
    suspend fun claim(
        contentId: Long,
        staleAfter: Duration,
        now: Instant,
    ): LinkContent?

    /** 클레임 당시 fence와 일치하는 PENDING 행에만 [next]를 쓴다. 0행이면 false. */
    suspend fun updateClaimed(
        fence: ClaimFence,
        next: LinkContent,
    ): Boolean

    suspend fun findDue(
        staleAfter: Duration,
        size: Int,
        now: Instant,
        dueBefore: Instant,
        after: DueKeyset?,
    ): List<LinkContentTargetQuery>

    suspend fun countDue(
        staleAfter: Duration,
        now: Instant,
    ): Long

    suspend fun countPending(): Long

    suspend fun nextRunnableAt(
        staleAfter: Duration,
        now: Instant,
    ): Instant?

    suspend fun oldestDueAge(
        staleAfter: Duration,
        now: Instant,
    ): Duration?
}
