package com.qlink.link.repository

import com.qlink.link.domain.Link
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.dto.SearchLinksQuery
import com.qlink.link.repository.table.Links
import com.qlink.link.repository.table.fromDomain
import com.qlink.link.repository.table.refreshUpdatedAt
import com.qlink.link.repository.table.toLinkDomain
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.updateReturning
import java.sql.ResultSet
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
        val cursorValue = cursor?.value
        val scoreSql = scoreSql(keywordProvided = normalizedQuery != null)
        val baseFilterSql = mutableListOf("l.owner_id = ?")
        val scoreArgs = mutableListOf<Pair<IColumnType<*>, Any?>>()
        val baseArgs = mutableListOf<Pair<IColumnType<*>, Any?>>(LongColumnType() to ownerId)
        val cursorArgs = mutableListOf<Pair<IColumnType<*>, Any?>>()

        if (normalizedQuery != null) {
            repeat(5) {
                scoreArgs += TextColumnType() to normalizedQuery
            }
        }

        if (folderId != null) {
            baseFilterSql += "l.folder_id = ?"
            baseArgs += LongColumnType() to folderId
        }

        if (normalizedQuery != null) {
            baseFilterSql += "l.search_text ILIKE '%' || ? || '%'"
            baseArgs += TextColumnType() to normalizedQuery
        }

        val sql =
            """
            WITH base AS (
                SELECT
                    l.id,
                    l.folder_id,
                    f.name AS folder_name,
                    f.emoji AS folder_emoji,
                    l.url,
                    l.title,
                    l.tags,
                    l.created_at,
                    ${scoreSql.title} AS title_score,
                    ${scoreSql.url} AS url_score,
                    ${scoreSql.tags} AS tags_score,
                    ${scoreSql.summary} AS summary_score,
                    ${scoreSql.memo} AS memo_score
                FROM links l
                LEFT JOIN folders f ON f.id = l.folder_id
                WHERE ${baseFilterSql.joinToString(" AND ")}
            ),
            scored AS (
                SELECT
                    id,
                    folder_id,
                    folder_name,
                    folder_emoji,
                    url,
                    title,
                    tags,
                    created_at,
                    ((title_score * 5.0) + (url_score * 3.0) + (tags_score * 2.0) + (summary_score * 1.0) + (memo_score * 0.5)) AS score,
                    title_score,
                    url_score,
                    tags_score,
                    summary_score,
                    memo_score
                FROM base
            )
            SELECT *
            FROM scored
            WHERE ${cursorFilterSql(order, cursorValue, cursorArgs)}
            ORDER BY ${orderBySql(order)}
            LIMIT ?
            """.trimIndent()

        val args =
            buildList {
                addAll(scoreArgs)
                addAll(baseArgs)
                addAll(cursorArgs)
                add(IntegerColumnType() to (size + 1))
            }

        return TransactionManager
            .current()
            .exec(sql, args, explicitStatementType = StatementType.SELECT) { resultSet ->
                generateSequence { if (resultSet.next()) resultSet.toSearchLinksQuery() else null }.toList()
            }.orEmpty()
    }

    override suspend fun update(link: Link): Link =
        Links
            .updateReturning(where = { Links.id eq link.id!! }) {
                it.fromDomain(link)
                it.refreshUpdatedAt()
            }.single()
            .toLinkDomain()

    override suspend fun deleteById(linkId: Long) {
        Links.deleteWhere { id eq linkId }
    }

    private fun scoreSql(keywordProvided: Boolean): ScoreSql =
        if (!keywordProvided) {
            ScoreSql(
                title = "0.0",
                url = "0.0",
                tags = "0.0",
                summary = "0.0",
                memo = "0.0",
            )
        } else {
            ScoreSql(
                title = "public.bigm_similarity(CAST(coalesce(l.title, '') AS text), CAST(? AS text))",
                url = "public.bigm_similarity(CAST(coalesce(l.url, '') AS text), CAST(? AS text))",
                tags = "public.bigm_similarity(CAST(array_to_string(coalesce(l.tags, '{}'), ' ') AS text), CAST(? AS text))",
                summary = "public.bigm_similarity(CAST(coalesce(l.summary, '') AS text), CAST(? AS text))",
                memo = "public.bigm_similarity(CAST(coalesce(l.memo, '') AS text), CAST(? AS text))",
            )
        }

    private fun cursorFilterSql(
        order: LinkSearchOrder,
        cursorValue: com.qlink.link.dto.LinkSearchCursorValue?,
        args: MutableList<Pair<IColumnType<*>, Any?>>,
    ): String =
        when (order) {
            LinkSearchOrder.LATEST -> {
                cursorValue?.id?.let {
                    args += LongColumnType() to it
                    "id < ?"
                } ?: "1 = 1"
            }

            LinkSearchOrder.EARLIEST -> {
                cursorValue?.id?.let {
                    args += LongColumnType() to it
                    "id > ?"
                } ?: "1 = 1"
            }

            LinkSearchOrder.LAXICO -> {
                cursorValue?.title?.let {
                    args += TextColumnType() to it
                    args += TextColumnType() to it
                    args += LongColumnType() to (cursorValue.id ?: Long.MIN_VALUE)
                    "(title > ? OR (title = ? AND id > ?))"
                } ?: "1 = 1"
            }

            LinkSearchOrder.SIMILAR -> {
                cursorValue?.score?.let {
                    similarCursorFilter(
                        values =
                            listOf(
                                "score" to (DoubleColumnType() to it),
                                "title_score" to (DoubleColumnType() to (cursorValue.titleScore ?: Double.MAX_VALUE)),
                                "url_score" to (DoubleColumnType() to (cursorValue.urlScore ?: Double.MAX_VALUE)),
                                "tags_score" to (DoubleColumnType() to (cursorValue.tagsScore ?: Double.MAX_VALUE)),
                                "summary_score" to (DoubleColumnType() to (cursorValue.summaryScore ?: Double.MAX_VALUE)),
                                "memo_score" to (DoubleColumnType() to (cursorValue.memoScore ?: Double.MAX_VALUE)),
                                "id" to (LongColumnType() to (cursorValue.id ?: Long.MAX_VALUE)),
                            ),
                        args = args,
                    )
                } ?: "1 = 1"
            }
        }

    private fun orderBySql(order: LinkSearchOrder): String =
        when (order) {
            LinkSearchOrder.LATEST -> {
                "id DESC"
            }

            LinkSearchOrder.EARLIEST -> {
                "id ASC"
            }

            LinkSearchOrder.LAXICO -> {
                "title ASC, id ASC"
            }

            LinkSearchOrder.SIMILAR -> {
                "score DESC, title_score DESC, url_score DESC, tags_score DESC, summary_score DESC, memo_score DESC, id DESC"
            }
        }

    private fun ResultSet.toSearchLinksQuery(): SearchLinksQuery =
        SearchLinksQuery(
            id = getLong("id"),
            folderId = getLong("folder_id").takeIf { !wasNull() },
            folderName = getString("folder_name"),
            folderEmoji = getString("folder_emoji"),
            url = getString("url"),
            title = getString("title"),
            tags = ((getArray("tags")?.array as? Array<*>)?.map { it.toString() }).orEmpty(),
            createdAt = getTimestamp("created_at").toInstant().toKotlinInstant(),
            score = getDouble("score"),
            titleScore = getDouble("title_score"),
            urlScore = getDouble("url_score"),
            tagsScore = getDouble("tags_score"),
            summaryScore = getDouble("summary_score"),
            memoScore = getDouble("memo_score"),
        )

    private data class ScoreSql(
        val title: String,
        val url: String,
        val tags: String,
        val summary: String,
        val memo: String,
    )

    private fun similarCursorFilter(
        values: List<Pair<String, Pair<IColumnType<*>, Any?>>>,
        args: MutableList<Pair<IColumnType<*>, Any?>>,
    ): String =
        values.indices.joinToString(" OR ", "(", ")") { index ->
            buildList {
                values.take(index).forEach { (column, parameter) ->
                    add("$column = ?")
                    args += parameter
                }
                add("${values[index].first} < ?")
                args += values[index].second
            }.joinToString(" AND ", "(", ")")
        }
}
