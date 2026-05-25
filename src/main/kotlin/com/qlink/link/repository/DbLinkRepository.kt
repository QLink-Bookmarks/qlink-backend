package com.qlink.link.repository

import com.qlink.common.search.arrayToString
import com.qlink.common.search.bigmSimilarity
import com.qlink.common.search.coalesceText
import com.qlink.common.search.doubleLiteral
import com.qlink.common.search.longLiteral
import com.qlink.common.search.lowerText
import com.qlink.link.domain.Link
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchCursorValue
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.dto.SearchLinksQuery
import com.qlink.link.repository.table.Links
import com.qlink.link.repository.table.fromDomain
import com.qlink.link.repository.table.refreshUpdatedAt
import com.qlink.link.repository.table.toLinkDomain
import com.qlink.folder.repository.table.Folders
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.toKotlinInstant

class DbLinkRepository : LinkRepository {
    override suspend fun insert(link: Link): Link = Links.insertReturning { it.fromDomain(link) }.single().toLinkDomain()

    override suspend fun findById(linkId: Long): Link? =
        Links
            .selectAll()
            .where { Links.id eq linkId }
            .singleOrNull()
            ?.toLinkDomain()

    override suspend fun search(
        ownerId: Long,
        query: String?,
        folderId: Long?,
        order: LinkSearchOrder,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery> {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val loweredQuery = normalizedQuery?.lowercase()
        val joined = Links.join(Folders, JoinType.LEFT, additionalConstraint = { Links.folderId eq Folders.id })

        val folderName = Folders.name.alias("folder_name")
        val folderEmoji = Folders.emoji.alias("folder_emoji")
        val titleScoreBase = scoreExpression(normalizedQuery) { keyword -> bigmSimilarity(Links.title, keyword) }
        val urlScoreBase = scoreExpression(normalizedQuery) { keyword -> bigmSimilarity(Links.url, keyword) }
        val tagsScoreBase = scoreExpression(normalizedQuery) { keyword -> bigmSimilarity(arrayToString(Links.tags, " "), keyword) }
        val summaryScoreBase = scoreExpression(normalizedQuery) { keyword -> bigmSimilarity(coalesceText(Links.summary), keyword) }
        val memoScoreBase = scoreExpression(normalizedQuery) { keyword -> bigmSimilarity(coalesceText(Links.memo), keyword) }
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
                    Links.url,
                    Links.title,
                    Links.tags,
                    Links.createdAt,
                    score,
                    titleScore,
                    urlScore,
                    tagsScore,
                    summaryScore,
                    memoScore,
                ).where { Links.ownerId eq ownerId }
                .apply {
                    folderId?.let { requestedFolderId ->
                        andWhere { Links.folderId eq requestedFolderId }
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

    override suspend fun detachFolder(folderId: Long) {
        Links.update(where = { Links.folderId eq folderId }) {
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
            LinkSearchOrder.LATEST -> Links.id less longLiteral(cursorValue.id ?: Long.MIN_VALUE)
            LinkSearchOrder.EARLIEST -> Links.id greater longLiteral(cursorValue.id ?: Long.MAX_VALUE)
            LinkSearchOrder.LAXICO ->
                (Links.title greater (cursorValue.title ?: "")) or
                    (
                        (Links.title eq (cursorValue.title ?: "")) and
                            (Links.id greater longLiteral(cursorValue.id ?: Long.MIN_VALUE))
                    )
            LinkSearchOrder.SIMILAR ->
                (score less doubleLiteral(cursorValue.score ?: Double.MIN_VALUE)) or
                    (
                        (score eq doubleLiteral(cursorValue.score ?: Double.MIN_VALUE)) and
                            (
                                (titleScore less doubleLiteral(cursorValue.titleScore ?: Double.MAX_VALUE)) or
                                    (
                                        (titleScore eq doubleLiteral(cursorValue.titleScore ?: Double.MAX_VALUE)) and
                                            (
                                                (urlScore less doubleLiteral(cursorValue.urlScore ?: Double.MAX_VALUE)) or
                                                    (
                                                        (urlScore eq doubleLiteral(cursorValue.urlScore ?: Double.MAX_VALUE)) and
                                                            (
                                                                (tagsScore less doubleLiteral(cursorValue.tagsScore ?: Double.MAX_VALUE)) or
                                                                    (
                                                                        (tagsScore eq doubleLiteral(cursorValue.tagsScore ?: Double.MAX_VALUE)) and
                                                                            (
                                                                                (summaryScore less doubleLiteral(cursorValue.summaryScore ?: Double.MAX_VALUE)) or
                                                                                    (
                                                                                        (summaryScore eq doubleLiteral(cursorValue.summaryScore ?: Double.MAX_VALUE)) and
                                                                                            (
                                                                                                (memoScore less doubleLiteral(cursorValue.memoScore ?: Double.MAX_VALUE)) or
                                                                                                    (
                                                                                                        (memoScore eq doubleLiteral(cursorValue.memoScore ?: Double.MAX_VALUE)) and
                                                                                                            (Links.id less longLiteral(cursorValue.id ?: Long.MAX_VALUE))
                                                                                                    )
                                                                                            )
                                                                                    )
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
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
            LinkSearchOrder.LATEST -> arrayOf(Links.id to SortOrder.DESC)
            LinkSearchOrder.EARLIEST -> arrayOf(Links.id to SortOrder.ASC)
            LinkSearchOrder.LAXICO -> arrayOf(Links.title to SortOrder.ASC, Links.id to SortOrder.ASC)
            LinkSearchOrder.SIMILAR ->
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

    private fun ResultRow.toSearchLinksQuery(
        folderName: Expression<String>,
        folderEmoji: Expression<String?>,
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
            url = this[Links.url],
            title = this[Links.title],
            tags = this[Links.tags],
            createdAt = this[Links.createdAt].toKotlinInstant(),
            score = this[score],
            titleScore = this[titleScore],
            urlScore = this[urlScore],
            tagsScore = this[tagsScore],
            summaryScore = this[summaryScore],
            memoScore = this[memoScore],
        )
}
