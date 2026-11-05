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
