package com.qlink.linkcontent.service

import com.qlink.common.transaction.TransactionRunner
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.dto.LinkContentScheduleQuery
import com.qlink.linkcontent.repository.LinkContentRepository
import kotlin.time.Instant

class GetLinkContentScheduleService(
    private val tx: TransactionRunner,
    private val linkContentRepository: LinkContentRepository,
) {
    suspend fun getSchedule(now: Instant): LinkContentScheduleQuery =
        tx.readOnly {
            LinkContentScheduleQuery(
                due = linkContentRepository.countDue(staleAfter = LinkContent.STALE_AFTER, now = now),
                pending = linkContentRepository.countPending(),
                nextRunnableAt = linkContentRepository.nextRunnableAt(staleAfter = LinkContent.STALE_AFTER, now = now),
                oldestDueAge = linkContentRepository.oldestDueAge(staleAfter = LinkContent.STALE_AFTER, now = now),
            )
        }
}
