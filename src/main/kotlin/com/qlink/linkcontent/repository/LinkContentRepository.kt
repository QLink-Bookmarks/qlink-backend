package com.qlink.linkcontent.repository

import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.dto.DueKeyset
import com.qlink.linkcontent.dto.LinkContentTargetQuery
import kotlin.time.Duration
import kotlin.time.Instant

interface LinkContentRepository {
    suspend fun insertAll(contents: List<LinkContent>)

    suspend fun findById(contentId: Long): LinkContent?

    suspend fun findByLinkId(linkId: Long): LinkContent?

    suspend fun upsertForLink(content: LinkContent): LinkContent

    suspend fun claim(
        contentId: Long,
        staleAfter: Duration,
        now: Instant,
    ): LinkContent?

    suspend fun updateCollectResult(content: LinkContent): Boolean

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
