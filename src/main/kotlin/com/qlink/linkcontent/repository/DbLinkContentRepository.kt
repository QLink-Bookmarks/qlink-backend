package com.qlink.linkcontent.repository

import com.qlink.common.text.hostOrNull
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.LinkContent
import com.qlink.linkcontent.dto.DueKeyset
import com.qlink.linkcontent.dto.LinkContentTargetQuery
import com.qlink.linkcontent.repository.table.LinkContents
import com.qlink.linkcontent.repository.table.fromDomain
import com.qlink.linkcontent.repository.table.fromDomainForInsert
import com.qlink.linkcontent.repository.table.refreshLinkContentUpdatedAt
import com.qlink.linkcontent.repository.table.toLinkContentDomain
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.jetbrains.exposed.v1.jdbc.upsertReturning
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

class DbLinkContentRepository : LinkContentRepository {
    override suspend fun insertAll(contents: List<LinkContent>) {
        if (contents.isEmpty()) {
            return
        }

        LinkContents.batchInsert(contents, ignore = true, shouldReturnGeneratedValues = false) { content ->
            fromDomainForInsert(content)
        }
    }

    override suspend fun findById(contentId: Long): LinkContent? =
        LinkContents
            .selectAll()
            .where { LinkContents.id eq contentId }
            .singleOrNull()
            ?.toLinkContentDomain()

    override suspend fun findByLinkId(linkId: Long): LinkContent? =
        LinkContents
            .selectAll()
            .where { LinkContents.linkId eq linkId }
            .singleOrNull()
            ?.toLinkContentDomain()

    override suspend fun upsertForLink(content: LinkContent): LinkContent =
        LinkContents
            .upsertReturning(
                LinkContents.linkId,
                onUpdate = { statement ->
                    statement.fromDomain(content)
                    statement.refreshLinkContentUpdatedAt()
                },
            ) {
                it.fromDomainForInsert(content)
            }.single()
            .toLinkContentDomain()

    override suspend fun claim(
        contentId: Long,
        staleAfter: Duration,
        now: Instant,
    ): LinkContent? =
        LinkContents
            .updateReturning(
                where = {
                    (LinkContents.id eq contentId) and
                        (LinkContents.nextAttemptAt lessEq now.toJavaInstant()) and
                        isRunnable(staleAfter, now)
                },
            ) {
                it[LinkContents.startedAt] = now.toJavaInstant()
                it[LinkContents.claimCount] = LinkContents.claimCount + 1.toShort()
                it.refreshLinkContentUpdatedAt()
            }.singleOrNull()
            ?.toLinkContentDomain()

    override suspend fun updateCollectResult(content: LinkContent): Boolean =
        LinkContents.update(
            where = {
                (LinkContents.id eq content.id!!) and
                    LinkContents.isPending() and
                    (LinkContents.sourceUrl eq content.sourceUrl)
            },
        ) {
            it.fromDomain(content)
            it.refreshLinkContentUpdatedAt()
        } == 1

    override suspend fun findDue(
        staleAfter: Duration,
        size: Int,
        now: Instant,
        dueBefore: Instant,
        after: DueKeyset?,
    ): List<LinkContentTargetQuery> =
        LinkContents
            .select(LinkContents.id, LinkContents.linkId, LinkContents.sourceUrl, LinkContents.priority, LinkContents.nextAttemptAt)
            .where {
                (LinkContents.nextAttemptAt lessEq dueBefore.toJavaInstant()) and
                    isRunnable(staleAfter, now) and
                    (after?.let(::isAfter) ?: Op.TRUE)
            }.orderBy(
                LinkContents.priority to SortOrder.ASC,
                LinkContents.nextAttemptAt to SortOrder.ASC,
                LinkContents.id to SortOrder.ASC,
            ).limit(size)
            .map {
                val url = it[LinkContents.sourceUrl]
                LinkContentTargetQuery(
                    contentId = it[LinkContents.id],
                    linkId = it[LinkContents.linkId],
                    url = url,
                    host = url.hostOrNull() ?: url,
                    keyset =
                        DueKeyset(
                            priority = ContentPriority.from(it[LinkContents.priority].toInt()),
                            nextAttemptAt = it[LinkContents.nextAttemptAt].toKotlinInstant(),
                            id = it[LinkContents.id],
                        ),
                )
            }

    override suspend fun countDue(
        staleAfter: Duration,
        now: Instant,
    ): Long =
        LinkContents
            .selectAll()
            .where { isDue(staleAfter, now) }
            .count()

    override suspend fun countPending(): Long =
        LinkContents
            .selectAll()
            .where { LinkContents.isPending() }
            .count()

    override suspend fun nextRunnableAt(
        staleAfter: Duration,
        now: Instant,
    ): Instant? {
        val javaNow = now.toJavaInstant()
        val nextUnstarted = LinkContents.nextAttemptAt.min()
        val unstartedAt =
            LinkContents
                .select(nextUnstarted)
                .where {
                    LinkContents.isPending() and
                        LinkContents.startedAt.isNull() and
                        (LinkContents.nextAttemptAt greater javaNow)
                }.single()[nextUnstarted]
                ?.toKotlinInstant()
        val earliestStarted = LinkContents.startedAt.min()
        val staleAt =
            LinkContents
                .select(earliestStarted)
                .where {
                    LinkContents.isPending() and
                        LinkContents.startedAt.isNotNull() and
                        (LinkContents.startedAt greater (now - staleAfter).toJavaInstant())
                }.single()[earliestStarted]
                ?.toKotlinInstant()
                ?.plus(staleAfter)

        return listOfNotNull(unstartedAt, staleAt).minOrNull()
    }

    override suspend fun oldestDueAge(
        staleAfter: Duration,
        now: Instant,
    ): Duration? {
        val oldest = LinkContents.nextAttemptAt.min()
        return LinkContents
            .select(oldest)
            .where { isDue(staleAfter, now) }
            .single()[oldest]
            ?.toKotlinInstant()
            ?.let { now - it }
    }

    private fun isDue(
        staleAfter: Duration,
        now: Instant,
    ): Op<Boolean> = (LinkContents.nextAttemptAt lessEq now.toJavaInstant()) and isRunnable(staleAfter, now)

    private fun isRunnable(
        staleAfter: Duration,
        now: Instant,
    ): Op<Boolean> =
        LinkContents.isPending() and
            (LinkContents.startedAt.isNull() or (LinkContents.startedAt less (now - staleAfter).toJavaInstant()))

    private fun isAfter(keyset: DueKeyset): Op<Boolean> {
        val priority = keyset.priority.value.toShort()
        val nextAttemptAt = keyset.nextAttemptAt.toJavaInstant()
        return (LinkContents.priority greater priority) or
            (
                (LinkContents.priority eq priority) and
                    (
                        (LinkContents.nextAttemptAt greater nextAttemptAt) or
                            ((LinkContents.nextAttemptAt eq nextAttemptAt) and (LinkContents.id greater keyset.id))
                    )
            )
    }
}
