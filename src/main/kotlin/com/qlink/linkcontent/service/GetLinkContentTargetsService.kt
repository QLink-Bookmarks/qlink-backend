package com.qlink.linkcontent.service

import com.qlink.common.transaction.TransactionRunner
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.dto.DueKeyset
import com.qlink.linkcontent.dto.LinkContentTargetQuery
import com.qlink.linkcontent.repository.LinkContentRepository
import kotlin.time.Instant

class GetLinkContentTargetsService(
    private val tx: TransactionRunner,
    private val linkContentRepository: LinkContentRepository,
) {
    /** sweep 시작 시각([dueBefore])까지 due이고 미실행(또는 stale)인 행을 우선순위·due·id 순으로 [after] 뒤부터 [size]개 읽는다. */
    suspend fun getTargets(
        size: Int,
        dueBefore: Instant,
        after: DueKeyset?,
        now: Instant,
    ): List<LinkContentTargetQuery> =
        tx.readOnly {
            linkContentRepository.findDue(
                staleAfter = LinkContent.STALE_AFTER,
                size = size,
                now = now,
                dueBefore = dueBefore,
                after = after,
            )
        }
}
