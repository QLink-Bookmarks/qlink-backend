package com.qlink.link.repository

import com.qlink.ai.repository.table.AvailableModels
import com.qlink.common.search.arrayToString
import com.qlink.common.search.bigmSimilarity
import com.qlink.common.search.coalesceText
import com.qlink.common.search.doubleLiteral
import com.qlink.common.search.longLiteral
import com.qlink.common.search.lowerText
import com.qlink.common.search.textLiteral
import com.qlink.folder.repository.table.Folders
import com.qlink.foldermember.repository.table.FolderMembers
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.dto.LinkDetailQuery
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchCursorValue
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.dto.SearchLinksQuery
import com.qlink.link.repository.table.Links
import com.qlink.link.repository.table.fromDomain
import com.qlink.link.repository.table.fromDomainForInsert
import com.qlink.link.repository.table.refreshUpdatedAt
import com.qlink.link.repository.table.toLinkDomain
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

class DbLinkRepository : LinkRepository {
    override suspend fun insert(link: Link): Link =
        Links
            .insertReturning { it.fromDomainForInsert(link) }
            .single()
            .toLinkDomain()

    override suspend fun insertAll(links: List<Link>): List<Link> =
        Links
            .batchInsert(links) { fromDomainForInsert(it) }
            .map { it.toLinkDomain() }

    override suspend fun findById(linkId: Long): Link? =
        Links
            .selectAll()
            .where { Links.id eq linkId }
            .singleOrNull()
            ?.toLinkDomain()

    override suspend fun findByIdForUpdate(linkId: Long): Link? =
        Links
            .selectAll()
            .where { Links.id eq linkId }
            .forUpdate()
            .singleOrNull()
            ?.toLinkDomain()

    override suspend fun findByIdForShare(linkId: Long): Link? =
        Links
            .selectAll()
            .where { Links.id eq linkId }
            .forUpdate(ForUpdateOption.PostgreSQL.ForShare)
            .singleOrNull()
            ?.toLinkDomain()

    override suspend fun findAllByFolderIdForUpdate(folderId: Long): List<Link> =
        Links
            .selectAll()
            .where { Links.folderId eq folderId }
            .orderBy(Links.id to SortOrder.ASC)
            .forUpdate()
            .map { it.toLinkDomain() }

    override suspend fun findAllByIdsForUpdate(linkIds: List<Long>): List<Link> =
        Links
            .selectAll()
            .where { Links.id inList linkIds.distinct().sorted() }
            .orderBy(Links.id to SortOrder.ASC)
            .forUpdate()
            .map { it.toLinkDomain() }

    override suspend fun assignFolderByIds(
        linkIds: List<Long>,
        folderId: Long?,
        now: Instant,
    ) {
        if (linkIds.isEmpty()) return
        Links.update(where = { Links.id inList linkIds }) {
            it[Links.folderId] = folderId
            it[Links.updatedAt] = now.toJavaInstant()
        }
    }

    override suspend fun findAllByIds(linkIds: List<Long>): List<Link> =
        Links
            .selectAll()
            .where { Links.id inList linkIds }
            .orderBy(Links.id to SortOrder.ASC)
            .map { it.toLinkDomain() }

    override suspend fun findAllByFolderIds(folderIds: List<Long>): List<Link> =
        Links
            .selectAll()
            .where { Links.folderId inList folderIds }
            .orderBy(Links.id to SortOrder.ASC)
            .map { it.toLinkDomain() }

    override suspend fun findAllUrlsByOwnerId(ownerId: Long): List<String> =
        Links
            .select(Links.url)
            .where { Links.ownerId eq ownerId }
            .map { it[Links.url] }

    override suspend fun findCreatedAtById(linkId: Long): Instant? =
        Links
            .select(Links.createdAt)
            .where { Links.id eq linkId }
            .singleOrNull()
            ?.let { it[Links.createdAt].toKotlinInstant() }

    override suspend fun findDetailById(
        linkId: Long,
        loginId: Long,
    ): LinkDetailQuery? {
        val joined =
            Links
                .join(
                    otherTable = Folders,
                    joinType = JoinType.LEFT,
                    additionalConstraint = { Links.folderId eq Folders.id },
                ).join(
                    otherTable = FolderMembers,
                    joinType = JoinType.LEFT,
                    additionalConstraint = {
                        (Folders.id eq FolderMembers.folderId) and
                            (FolderMembers.userId eq loginId)
                    },
                ).join(
                    otherTable = AvailableModels,
                    joinType = JoinType.LEFT,
                    additionalConstraint = { Links.workModelId eq AvailableModels.id },
                )
        val folderName = Folders.name.alias("folder_name")
        val folderEmoji = Folders.emoji.alias("folder_emoji")
        val workModel = AvailableModels.model.alias("work_model")

        return joined
            .select(
                Links.id,
                Links.ownerId,
                Links.folderId,
                Folders.sharedAt,
                FolderMembers.userId,
                folderName,
                folderEmoji,
                Links.url,
                Links.title,
                Links.thumbnailUrl,
                Links.faviconUrl,
                Links.isVideo,
                Links.summary,
                Links.memo,
                Links.tags,
                Links.sourceType,
                Links.status,
                Links.workModelId,
                workModel,
                Links.createdAt,
                Links.favoriteAt,
            ).where { Links.id eq linkId }
            .singleOrNull()
            ?.toLinkDetailQuery(
                folderName = folderName,
                folderEmoji = folderEmoji,
                workModel = workModel,
            )
    }

    override suspend fun search(
        ownerId: Long,
        query: String?,
        folderId: Long?,
        isFavorite: Boolean?,
        order: LinkSearchOrder,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery> {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val loweredQuery = normalizedQuery?.lowercase()
        val joined =
            Links
                .join(
                    otherTable = Folders,
                    joinType = JoinType.LEFT,
                    additionalConstraint = { Links.folderId eq Folders.id },
                ).join(
                    otherTable = FolderMembers,
                    joinType = JoinType.LEFT,
                    additionalConstraint = {
                        (Folders.id eq FolderMembers.folderId) and
                            (FolderMembers.userId eq ownerId)
                    },
                ).join(
                    otherTable = AvailableModels,
                    joinType = JoinType.LEFT,
                    additionalConstraint = { Links.workModelId eq AvailableModels.id },
                )

        val folderName = Folders.name.alias("folder_name")
        val folderEmoji = Folders.emoji.alias("folder_emoji")
        val workModel = AvailableModels.model.alias("work_model")
        val titleScoreBase =
            scoreExpression(normalizedQuery) { keyword ->
                bigmSimilarity(Links.title, keyword)
            }
        val urlScoreBase =
            scoreExpression(normalizedQuery) { keyword ->
                bigmSimilarity(Links.url, keyword)
            }
        val tagsScoreBase =
            scoreExpression(normalizedQuery) { keyword ->
                bigmSimilarity(arrayToString(Links.tags, " "), keyword)
            }
        val summaryScoreBase =
            scoreExpression(normalizedQuery) { keyword ->
                bigmSimilarity(coalesceText(Links.summary), keyword)
            }
        val memoScoreBase =
            scoreExpression(normalizedQuery) { keyword ->
                bigmSimilarity(coalesceText(Links.memo), keyword)
            }
        val titleScore = titleScoreBase.alias("title_score")
        val urlScore = urlScoreBase.alias("url_score")
        val tagsScore = tagsScoreBase.alias("tags_score")
        val summaryScore = summaryScoreBase.alias("summary_score")
        val memoScore = memoScoreBase.alias("memo_score")
        val scoreBase =
            titleScoreBase.times(doubleLiteral(5.0)) +
                urlScoreBase.times(doubleLiteral(3.0)) +
                tagsScoreBase.times(doubleLiteral(2.0)) +
                summaryScoreBase.times(doubleLiteral(1.0)) +
                memoScoreBase.times(doubleLiteral(0.5))
        val score = scoreBase.alias("score")

        val searchQuery =
            joined
                .select(
                    Links.id,
                    Links.folderId,
                    folderName,
                    folderEmoji,
                    workModel,
                    Links.url,
                    Links.title,
                    Links.thumbnailUrl,
                    Links.faviconUrl,
                    Links.isVideo,
                    Links.status,
                    Links.tags,
                    Links.createdAt,
                    Links.favoriteAt,
                    score,
                    titleScore,
                    urlScore,
                    tagsScore,
                    summaryScore,
                    memoScore,
                ).where {
                    (Links.ownerId eq ownerId) or
                        (
                            (FolderMembers.userId eq ownerId) and
                                Folders.sharedAt.isNotNull()
                        )
                }.apply {
                    folderId?.let { requestedFolderId ->
                        when (requestedFolderId) {
                            0L -> andWhere { Links.folderId.isNull() }
                            else -> andWhere { Links.folderId eq requestedFolderId }
                        }
                    }
                    isFavorite?.let { favorite ->
                        when (favorite) {
                            true -> andWhere { Links.favoriteAt.isNotNull() }
                            false -> andWhere { Links.favoriteAt.isNull() }
                        }
                    }
                    loweredQuery?.let { keyword ->
                        andWhere { lowerText(Links.searchText) like "%$keyword%" }
                    }
                    cursor?.value?.let { cursorValue ->
                        andWhere {
                            cursorFilter(
                                order = order,
                                cursorValue = cursorValue,
                                score = scoreBase,
                                titleScore = titleScoreBase,
                                urlScore = urlScoreBase,
                                tagsScore = tagsScoreBase,
                                summaryScore = summaryScoreBase,
                                memoScore = memoScoreBase,
                            )
                        }
                    }
                }.orderBy(*orderBy(order, scoreBase, titleScoreBase, urlScoreBase, tagsScoreBase, summaryScoreBase, memoScoreBase))
                .limit(size + 1)

        return searchQuery.map {
            it.toSearchLinksQuery(
                folderName = folderName,
                folderEmoji = folderEmoji,
                workModel = workModel,
                score = score,
                titleScore = titleScore,
                urlScore = urlScore,
                tagsScore = tagsScore,
                summaryScore = summaryScore,
                memoScore = memoScore,
            )
        }
    }

    override suspend fun update(link: Link): Link =
        Links
            .updateReturning(where = { Links.id eq link.id!! }) {
                it.fromDomain(link)
                it.refreshUpdatedAt()
            }.single()
            .toLinkDomain()

    override suspend fun updateFavorite(
        linkId: Long,
        favoriteAt: Instant?,
    ) {
        Links.update(where = { Links.id eq linkId }) {
            it[Links.favoriteAt] = favoriteAt?.toJavaInstant()
            it.refreshUpdatedAt()
        }
    }

    override suspend fun updatePatchFields(
        linkId: Long,
        folderId: Long?,
        memo: String?,
        tags: List<String>,
    ): Link =
        Links
            .updateReturning(where = { Links.id eq linkId }) {
                it[Links.folderId] = folderId
                it[Links.memo] = memo
                it[Links.tags] = tags
                it[Links.searchText] =
                    searchTextOf(
                        title = Links.title,
                        url = Links.url,
                        tags = textLiteral(tags.joinToString(" ")),
                        summary = coalesceText(Links.summary),
                        memo = textLiteral(memo.orEmpty()),
                    )
                it.refreshUpdatedAt()
            }.single()
            .toLinkDomain()

    override suspend fun updateAiResult(
        linkId: Long,
        folderId: Long?,
        title: String,
        summary: String?,
        tags: List<String>,
        status: LinkStatus,
        workModelId: Long?,
    ) {
        Links.update(where = { Links.id eq linkId }) {
            it[Links.folderId] = folderId
            it[Links.title] = title
            it[Links.summary] = summary
            it[Links.tags] = tags
            it[Links.status] = status
            it[Links.workModelId] = workModelId
            it[Links.searchText] =
                searchTextOf(
                    title = textLiteral(title),
                    url = Links.url,
                    tags = textLiteral(tags.joinToString(" ")),
                    summary = textLiteral(summary.orEmpty()),
                    memo = coalesceText(Links.memo),
                )
            it.refreshUpdatedAt()
        }
    }

    override suspend fun updateAiFailure(
        linkId: Long,
        title: String,
        summary: String?,
        status: LinkStatus,
    ) {
        Links.update(where = { Links.id eq linkId }) {
            it[Links.title] = title
            it[Links.summary] = summary
            it[Links.status] = status
            it[Links.searchText] =
                searchTextOf(
                    title = textLiteral(title),
                    url = Links.url,
                    tags = arrayToString(Links.tags, " "),
                    summary = textLiteral(summary.orEmpty()),
                    memo = coalesceText(Links.memo),
                )
            it.refreshUpdatedAt()
        }
    }

    override suspend fun updatePreview(link: Link): Boolean =
        Links.update(
            where = { (Links.id eq link.id!!) and Links.thumbnailUrl.isNull() and Links.faviconUrl.isNull() },
        ) {
            it[Links.thumbnailUrl] = link.thumbnailUrl
            it[Links.faviconUrl] = link.faviconUrl
            it[Links.isVideo] = link.isVideo
        } == 1

    override suspend fun detachFolder(folderId: Long) {
        Links.update(where = { Links.folderId eq folderId }) {
            it[Links.folderId] = null
            it.refreshUpdatedAt()
        }
    }

    override suspend fun detachFolders(folderIds: List<Long>) {
        Links.update(where = { Links.folderId inList folderIds }) {
            it[Links.folderId] = null
            it.refreshUpdatedAt()
        }
    }

    override suspend fun deleteAllByFolderId(folderId: Long) {
        Links.deleteWhere { Links.folderId eq folderId }
    }

    override suspend fun deleteById(linkId: Long) {
        Links.deleteWhere { id eq linkId }
    }

    override suspend fun deleteAllByIds(linkIds: List<Long>) {
        Links.deleteWhere { id inList linkIds }
    }

    /**
     * `links.search_text`를 SQL에서 만든다. 테이블 매핑의 `Link.searchText()`와 같은 순서·구분자(공백)다.
     * UPDATE의 SET 식은 갱신 전 행을 읽으므로, 이번에 쓰는 필드는 새 값(파라미터)으로, 쓰지 않는 필드는 컬럼으로 넘긴다.
     */
    private fun searchTextOf(
        title: Expression<*>,
        url: Expression<*>,
        tags: Expression<*>,
        summary: Expression<*>,
        memo: Expression<*>,
    ): Expression<String> = concat(separator = " ", expr = listOf(title, url, tags, summary, memo))

    private fun scoreExpression(
        keyword: String?,
        create: (String) -> ExpressionWithColumnType<Double>,
    ): ExpressionWithColumnType<Double> = keyword?.let(create) ?: doubleLiteral(0.0)

    private fun cursorFilter(
        order: LinkSearchOrder,
        cursorValue: LinkSearchCursorValue,
        score: ExpressionWithColumnType<Double>,
        titleScore: ExpressionWithColumnType<Double>,
        urlScore: ExpressionWithColumnType<Double>,
        tagsScore: ExpressionWithColumnType<Double>,
        summaryScore: ExpressionWithColumnType<Double>,
        memoScore: ExpressionWithColumnType<Double>,
    ): Op<Boolean> =
        when (order) {
            LinkSearchOrder.LATEST -> {
                val createdAt = cursorValue.createdAt!!.toJavaInstant()

                (Links.createdAt less createdAt) or
                    ((Links.createdAt eq createdAt) and (Links.id less cursorValue.id!!))
            }

            LinkSearchOrder.EARLIEST -> {
                val createdAt = cursorValue.createdAt!!.toJavaInstant()

                (Links.createdAt greater createdAt) or
                    ((Links.createdAt eq createdAt) and (Links.id greater cursorValue.id!!))
            }

            LinkSearchOrder.LAXICO -> {
                (Links.title greater (cursorValue.title ?: "")) or
                    (
                        (Links.title eq (cursorValue.title ?: "")) and
                            (Links.id greater longLiteral(cursorValue.id ?: Long.MIN_VALUE))
                    )
            }

            LinkSearchOrder.SIMILAR -> {
                similarCursorFilter(
                    cursorValue = cursorValue,
                    score = score,
                    titleScore = titleScore,
                    urlScore = urlScore,
                    tagsScore = tagsScore,
                    summaryScore = summaryScore,
                    memoScore = memoScore,
                )
            }
        }

    private fun similarCursorFilter(
        cursorValue: LinkSearchCursorValue,
        score: ExpressionWithColumnType<Double>,
        titleScore: ExpressionWithColumnType<Double>,
        urlScore: ExpressionWithColumnType<Double>,
        tagsScore: ExpressionWithColumnType<Double>,
        summaryScore: ExpressionWithColumnType<Double>,
        memoScore: ExpressionWithColumnType<Double>,
    ): Op<Boolean> {
        val scoreLiteral = doubleLiteral(cursorValue.score ?: Double.MIN_VALUE)

        return (score less scoreLiteral) or
            (
                (score eq scoreLiteral) and
                    titleScoreCursorFilter(
                        cursorValue = cursorValue,
                        titleScore = titleScore,
                        urlScore = urlScore,
                        tagsScore = tagsScore,
                        summaryScore = summaryScore,
                        memoScore = memoScore,
                    )
            )
    }

    private fun titleScoreCursorFilter(
        cursorValue: LinkSearchCursorValue,
        titleScore: ExpressionWithColumnType<Double>,
        urlScore: ExpressionWithColumnType<Double>,
        tagsScore: ExpressionWithColumnType<Double>,
        summaryScore: ExpressionWithColumnType<Double>,
        memoScore: ExpressionWithColumnType<Double>,
    ): Op<Boolean> {
        val titleScoreLiteral = doubleLiteral(cursorValue.titleScore ?: Double.MAX_VALUE)

        return (titleScore less titleScoreLiteral) or
            (
                (titleScore eq titleScoreLiteral) and
                    urlScoreCursorFilter(
                        cursorValue = cursorValue,
                        urlScore = urlScore,
                        tagsScore = tagsScore,
                        summaryScore = summaryScore,
                        memoScore = memoScore,
                    )
            )
    }

    private fun urlScoreCursorFilter(
        cursorValue: LinkSearchCursorValue,
        urlScore: ExpressionWithColumnType<Double>,
        tagsScore: ExpressionWithColumnType<Double>,
        summaryScore: ExpressionWithColumnType<Double>,
        memoScore: ExpressionWithColumnType<Double>,
    ): Op<Boolean> {
        val urlScoreLiteral = doubleLiteral(cursorValue.urlScore ?: Double.MAX_VALUE)

        return (urlScore less urlScoreLiteral) or
            (
                (urlScore eq urlScoreLiteral) and
                    tagsScoreCursorFilter(
                        cursorValue = cursorValue,
                        tagsScore = tagsScore,
                        summaryScore = summaryScore,
                        memoScore = memoScore,
                    )
            )
    }

    private fun tagsScoreCursorFilter(
        cursorValue: LinkSearchCursorValue,
        tagsScore: ExpressionWithColumnType<Double>,
        summaryScore: ExpressionWithColumnType<Double>,
        memoScore: ExpressionWithColumnType<Double>,
    ): Op<Boolean> {
        val tagsScoreLiteral = doubleLiteral(cursorValue.tagsScore ?: Double.MAX_VALUE)

        return (tagsScore less tagsScoreLiteral) or
            (
                (tagsScore eq tagsScoreLiteral) and
                    summaryScoreCursorFilter(
                        cursorValue = cursorValue,
                        summaryScore = summaryScore,
                        memoScore = memoScore,
                    )
            )
    }

    private fun summaryScoreCursorFilter(
        cursorValue: LinkSearchCursorValue,
        summaryScore: ExpressionWithColumnType<Double>,
        memoScore: ExpressionWithColumnType<Double>,
    ): Op<Boolean> {
        val summaryScoreLiteral = doubleLiteral(cursorValue.summaryScore ?: Double.MAX_VALUE)

        return (summaryScore less summaryScoreLiteral) or
            (
                (summaryScore eq summaryScoreLiteral) and
                    memoScoreCursorFilter(
                        cursorValue = cursorValue,
                        memoScore = memoScore,
                    )
            )
    }

    private fun memoScoreCursorFilter(
        cursorValue: LinkSearchCursorValue,
        memoScore: ExpressionWithColumnType<Double>,
    ): Op<Boolean> {
        val memoScoreLiteral = doubleLiteral(cursorValue.memoScore ?: Double.MAX_VALUE)
        val idLiteral = longLiteral(cursorValue.id ?: Long.MAX_VALUE)

        return (memoScore less memoScoreLiteral) or
            (
                (memoScore eq memoScoreLiteral) and
                    (Links.id less idLiteral)
            )
    }

    private fun orderBy(
        order: LinkSearchOrder,
        score: ExpressionWithColumnType<Double>,
        titleScore: ExpressionWithColumnType<Double>,
        urlScore: ExpressionWithColumnType<Double>,
        tagsScore: ExpressionWithColumnType<Double>,
        summaryScore: ExpressionWithColumnType<Double>,
        memoScore: ExpressionWithColumnType<Double>,
    ): Array<Pair<Expression<*>, SortOrder>> =
        when (order) {
            LinkSearchOrder.LATEST -> {
                arrayOf(Links.createdAt to SortOrder.DESC, Links.id to SortOrder.DESC)
            }

            LinkSearchOrder.EARLIEST -> {
                arrayOf(Links.createdAt to SortOrder.ASC, Links.id to SortOrder.ASC)
            }

            LinkSearchOrder.LAXICO -> {
                arrayOf(Links.title to SortOrder.ASC, Links.id to SortOrder.ASC)
            }

            LinkSearchOrder.SIMILAR -> {
                arrayOf(
                    score to SortOrder.DESC,
                    titleScore to SortOrder.DESC,
                    urlScore to SortOrder.DESC,
                    tagsScore to SortOrder.DESC,
                    summaryScore to SortOrder.DESC,
                    memoScore to SortOrder.DESC,
                    Links.id to SortOrder.DESC,
                )
            }
        }

    private fun ResultRow.toSearchLinksQuery(
        folderName: Expression<String>,
        folderEmoji: Expression<String?>,
        workModel: Expression<String>,
        score: Expression<Double>,
        titleScore: Expression<Double>,
        urlScore: Expression<Double>,
        tagsScore: Expression<Double>,
        summaryScore: Expression<Double>,
        memoScore: Expression<Double>,
    ): SearchLinksQuery =
        SearchLinksQuery(
            id = this[Links.id],
            folderId = this[Links.folderId],
            folderName = this[folderName],
            folderEmoji = this[folderEmoji],
            workModel = this[workModel],
            url = this[Links.url],
            title = this[Links.title],
            thumbnailUrl = this[Links.thumbnailUrl],
            faviconUrl = this[Links.faviconUrl],
            isVideo = this[Links.isVideo],
            status = this[Links.status],
            tags = this[Links.tags],
            createdAt = this[Links.createdAt].toKotlinInstant(),
            favoriteAt = this[Links.favoriteAt]?.toKotlinInstant(),
            score = this[score],
            titleScore = this[titleScore],
            urlScore = this[urlScore],
            tagsScore = this[tagsScore],
            summaryScore = this[summaryScore],
            memoScore = this[memoScore],
        )

    private fun ResultRow.toLinkDetailQuery(
        folderName: Expression<String>,
        folderEmoji: Expression<String?>,
        workModel: Expression<String>,
    ): LinkDetailQuery =
        LinkDetailQuery(
            id = this[Links.id],
            ownerId = this[Links.ownerId],
            folderId = this[Links.folderId],
            folderSharedAt = this[Folders.sharedAt]?.toKotlinInstant(),
            folderMemberUserId = this[FolderMembers.userId],
            folderName = this[folderName],
            folderEmoji = this[folderEmoji],
            url = this[Links.url],
            title = this[Links.title],
            thumbnailUrl = this[Links.thumbnailUrl],
            faviconUrl = this[Links.faviconUrl],
            isVideo = this[Links.isVideo],
            summary = this[Links.summary],
            memo = this[Links.memo],
            tags = this[Links.tags],
            sourceType = this[Links.sourceType],
            status = this[Links.status],
            workModelId = this[Links.workModelId],
            workModel = this[workModel],
            createdAt = this[Links.createdAt].toKotlinInstant(),
            favoriteAt = this[Links.favoriteAt]?.toKotlinInstant(),
        )
}
