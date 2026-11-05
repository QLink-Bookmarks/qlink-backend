@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.linkcontent.repository.table

import com.qlink.link.repository.table.Links
import com.qlink.linkcontent.domain.CollectedPage
import com.qlink.linkcontent.domain.ContentFailureCode
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.domain.LinkContent
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlin.uuid.ExperimentalUuidApi

object LinkContents : Table("link_contents") {
    val id = long("id").autoIncrement()
    val linkId = reference("link_id", Links.id, onDelete = ReferenceOption.CASCADE).uniqueIndex("link_contents_link_id_unique")
    val sourceUrl = text("source_url")
    val status = enumerationByName<ContentStatus>("status", 20)
    val priority = short("priority").default(0)
    val generation = integer("generation").default(0)
    val claimToken = uuid("claim_token").nullable()
    val claimCount = short("claim_count").default(0)
    val attempt = short("attempt").default(0)
    val startedAt = timestamp("started_at").nullable()
    val nextAttemptAt = timestamp("next_attempt_at").defaultExpression(CurrentTimestamp)
    val failureCode = enumerationByName<ContentFailureCode>("failure_code", 40).nullable()
    val canonicalUrl = text("canonical_url").nullable()
    val finalUrl = text("final_url").nullable()
    val title = varchar("title", CollectedPage.MAX_TITLE_LENGTH).nullable()
    val siteName = varchar("site_name", CollectedPage.MAX_SITE_NAME_LENGTH).nullable()
    val author = varchar("author", CollectedPage.MAX_AUTHOR_LENGTH).nullable()
    val publishedAt = timestamp("published_at").nullable()
    val language = varchar("language", CollectedPage.MAX_LANGUAGE_LENGTH).nullable()
    val contentText = text("content_text").nullable()
    val contentLength = integer("content_length").nullable()
    val contentHash = char("content_hash", 64).nullable()
    val isTruncated = bool("is_truncated").default(false)
    val excerpt = varchar("excerpt", 101).nullable()
    val collectedAt = timestamp("collected_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toLinkContentDomain(): LinkContent =
    LinkContent(
        id = this[LinkContents.id],
        linkId = this[LinkContents.linkId],
        sourceUrl = this[LinkContents.sourceUrl],
        status = this[LinkContents.status],
        priority = ContentPriority.from(this[LinkContents.priority].toInt()),
        generation = this[LinkContents.generation],
        claimToken = this[LinkContents.claimToken],
        claimCount = this[LinkContents.claimCount].toInt(),
        attempt = this[LinkContents.attempt].toInt(),
        startedAt = this[LinkContents.startedAt]?.toKotlinInstant(),
        nextAttemptAt = this[LinkContents.nextAttemptAt].toKotlinInstant(),
        failureCode = this[LinkContents.failureCode],
        page = toCollectedPageOrNull(),
        contentHash = this[LinkContents.contentHash],
        excerpt = this[LinkContents.excerpt],
        collectedAt = this[LinkContents.collectedAt]?.toKotlinInstant(),
        createdAt = this[LinkContents.createdAt].toKotlinInstant(),
        updatedAt = this[LinkContents.updatedAt].toKotlinInstant(),
    )

private fun ResultRow.toCollectedPageOrNull(): CollectedPage? =
    this[LinkContents.contentText]?.let { text ->
        CollectedPage(
            text = text,
            title = this[LinkContents.title],
            siteName = this[LinkContents.siteName],
            author = this[LinkContents.author],
            publishedAt = this[LinkContents.publishedAt]?.toKotlinInstant(),
            canonicalUrl = this[LinkContents.canonicalUrl],
            finalUrl = this[LinkContents.finalUrl],
            language = this[LinkContents.language],
            isTruncated = this[LinkContents.isTruncated],
        )
    }

fun UpdateBuilder<*>.fromDomainState(content: LinkContent) {
    val page = content.page
    this[LinkContents.sourceUrl] = content.sourceUrl
    this[LinkContents.status] = content.status
    this[LinkContents.priority] = content.priority.value.toShort()
    this[LinkContents.claimToken] = content.claimToken
    this[LinkContents.claimCount] = content.claimCount.toShort()
    this[LinkContents.attempt] = content.attempt.toShort()
    this[LinkContents.startedAt] = content.startedAt?.toJavaInstant()
    this[LinkContents.nextAttemptAt] = content.nextAttemptAt.toJavaInstant()
    this[LinkContents.failureCode] = content.failureCode
    this[LinkContents.canonicalUrl] = page?.canonicalUrl
    this[LinkContents.finalUrl] = page?.finalUrl
    this[LinkContents.title] = page?.title
    this[LinkContents.siteName] = page?.siteName
    this[LinkContents.author] = page?.author
    this[LinkContents.publishedAt] = page?.publishedAt?.toJavaInstant()
    this[LinkContents.language] = page?.language
    this[LinkContents.contentText] = page?.text
    this[LinkContents.contentLength] = content.contentLength
    this[LinkContents.contentHash] = content.contentHash
    this[LinkContents.isTruncated] = page?.isTruncated ?: false
    this[LinkContents.excerpt] = content.excerpt
    this[LinkContents.collectedAt] = content.collectedAt?.toJavaInstant()
}

fun UpdateBuilder<*>.fromNewDomain(content: LinkContent) {
    this[LinkContents.linkId] = content.linkId
    this[LinkContents.generation] = content.generation
    fromDomainState(content)
}

fun UpdateBuilder<*>.refreshLinkContentUpdatedAt() {
    this[LinkContents.updatedAt] = Clock.System.now().toJavaInstant()
}
