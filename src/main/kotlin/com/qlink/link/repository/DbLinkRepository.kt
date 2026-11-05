package com.qlink.link.repository

import com.qlink.ai.repository.table.AvailableModels
import com.qlink.common.search.arrayToString
import com.qlink.common.search.bigmSimilarity
import com.qlink.common.search.boolOf
import com.qlink.common.search.boolOr
import com.qlink.common.search.boolToInt
import com.qlink.common.search.coalesceText
import com.qlink.common.search.doubleLiteral
import com.qlink.common.search.greatestInt
import com.qlink.common.search.intLiteral
import com.qlink.common.search.leastDouble
import com.qlink.common.search.likeQuery
import com.qlink.common.search.longLiteral
import com.qlink.common.search.lowerText
import com.qlink.common.search.textLiteral
import com.qlink.common.search.textPosition
import com.qlink.folder.repository.table.Folders
import com.qlink.foldermember.repository.table.FolderMembers
import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.SearchKeyword
import com.qlink.link.domain.Snippet
import com.qlink.link.dto.LinkDetailQuery
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchCursorValue
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.dto.LinkSnippetQuery
import com.qlink.link.dto.SearchLinksQuery
import com.qlink.link.repository.table.Links
import com.qlink.link.repository.table.fromDomain
import com.qlink.link.repository.table.fromDomainForInsert
import com.qlink.link.repository.table.refreshUpdatedAt
import com.qlink.link.repository.table.toLinkDomain
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.repository.table.LinkContents
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.EqOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.GreaterOp
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.LessOp
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Substring
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.booleanLiteral
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.charLength
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.unionAll
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
                ).join(
                    otherTable = LinkContents,
                    joinType = JoinType.LEFT,
                    additionalConstraint = { LinkContents.linkId eq Links.id },
                )
        val folderName = Folders.name.alias("folder_name")
        val folderEmoji = Folders.emoji.alias("folder_emoji")
        val workModel = AvailableModels.model.alias("work_model")
        val hasContent = boolOf(LinkContents.id.isNotNull()).alias("has_content")
        val contentExcerpt =
            Case()
                .When(LinkContents.status eq ContentStatus.COLLECTED, LinkContents.excerpt)
                .Else(Op.nullOp())
                .alias("content_excerpt")
        val isContentPending = boolOf(LinkContents.status eq ContentStatus.PENDING).alias("is_content_pending")

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
                hasContent,
                contentExcerpt,
                isContentPending,
            ).where { Links.id eq linkId }
            .singleOrNull()
            ?.let { row ->
                row.toLinkDetailQuery(
                    folderName = folderName,
                    folderEmoji = folderEmoji,
                    workModel = workModel,
                    hasContent = hasContent,
                    contentExcerpt = contentExcerpt,
                    isContentPending = isContentPending,
                )
            }
    }

    /**
     * 검색어가 없으면 접근 가능한 링크를 요청한 정렬로 읽는다(기존과 같은 순서·커서).
     * 검색어가 있으면 메타데이터 일치 후보와 본문 일치 후보를 `UNION ALL`로 모아 링크별로 합치고,
     * 메타 일치 묶음을 먼저 둔 뒤 요청한 정렬을 적용한다. 본문 일치 행에는 스니펫 창을 붙인다.
     */
    override suspend fun search(
        ownerId: Long,
        keyword: SearchKeyword?,
        folderId: Long?,
        isFavorite: Boolean?,
        order: LinkSearchOrder,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery> {
        if (keyword == null) {
            return searchAll(
                ownerId = ownerId,
                folderId = folderId,
                isFavorite = isFavorite,
                order = order,
                cursor = cursor,
                size = size,
            )
        }

        val page =
            searchByKeyword(
                ownerId = ownerId,
                keyword = keyword,
                folderId = folderId,
                isFavorite = isFavorite,
                order = order,
                cursor = cursor,
                size = size,
            )
        val snippets =
            findSnippets(
                linkIds = page.filter { it.contentMatched }.map { it.id },
                keyword = keyword.value,
            )

        return page.map { row ->
            snippets[row.id]?.let { snippet ->
                row.copy(
                    snippetSource = snippet.source,
                    snippetHeadCut = snippet.headCut,
                    snippetTailCut = snippet.tailCut,
                    snippetBodyLength = snippet.bodyLength,
                )
            } ?: row
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

    private fun searchTextOf(
        title: Expression<*>,
        url: Expression<*>,
        tags: Expression<*>,
        summary: Expression<*>,
        memo: Expression<*>,
    ): Expression<String> = concat(separator = " ", expr = listOf(title, url, tags, summary, memo))

    private fun searchAll(
        ownerId: Long,
        folderId: Long?,
        isFavorite: Boolean?,
        order: LinkSearchOrder,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery> {
        val columns = SearchColumns.withoutKeyword()
        val joined =
            accessibleLinks(ownerId)
                .join(
                    otherTable = AvailableModels,
                    joinType = JoinType.LEFT,
                    additionalConstraint = { Links.workModelId eq AvailableModels.id },
                ).joinCollectedContent()

        return selectPage(
            joined = joined,
            columns = columns,
            condition = accessibleWith(ownerId = ownerId, folderId = folderId, isFavorite = isFavorite),
            sortKeys = sortKeys(order = order, columns = columns, hasKeyword = false),
            cursor = cursor,
            size = size,
        )
    }

    private fun searchByKeyword(
        ownerId: Long,
        keyword: SearchKeyword,
        folderId: Long?,
        isFavorite: Boolean?,
        order: LinkSearchOrder,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery> {
        val pattern = likeQuery(keyword.value)
        val accessible = accessibleWith(ownerId = ownerId, folderId = folderId, isFavorite = isFavorite)
        val candidateLinkId = Links.id.alias("link_id")
        val candidateMetaHit = booleanLiteral(true).alias("meta_hit")
        val candidateContentHit = booleanLiteral(false).alias("content_hit")
        // search_text는 GIN 인덱스를 타는 사전 필터이고, 실제 일치는 필드별로 본다(필드 경계를 가로지른 후보 제외).
        val metadataCandidates =
            accessibleLinks(ownerId)
                .select(candidateLinkId, candidateMetaHit, candidateContentHit)
                .where {
                    accessible and
                        (lowerText(Links.searchText) like pattern) and
                        METADATA_FIELDS.map { matches(it, pattern) }.reduce { left, right -> left or right }
                }
        val candidates =
            if (keyword.includesBody) {
                metadataCandidates.unionAll(contentCandidates(ownerId = ownerId, accessible = accessible, pattern = pattern))
            } else {
                metadataCandidates
            }.alias("candidates")
        val hitLinkId = candidates[candidateLinkId].alias("link_id")
        val hitIsMetadataMatched = boolOr(candidates[candidateMetaHit]).alias("is_meta")
        val hitContentMatched = boolOr(candidates[candidateContentHit]).alias("content_matched")
        val hits =
            candidates
                .select(hitLinkId, hitIsMetadataMatched, hitContentMatched)
                .groupBy(candidates[candidateLinkId])
                .alias("hits")
        val joined =
            hits
                .join(
                    otherTable = Links,
                    joinType = JoinType.INNER,
                    onColumn = hits[hitLinkId],
                    otherColumn = Links.id,
                ).join(
                    otherTable = Folders,
                    joinType = JoinType.LEFT,
                    additionalConstraint = { Links.folderId eq Folders.id },
                ).join(
                    otherTable = AvailableModels,
                    joinType = JoinType.LEFT,
                    additionalConstraint = { Links.workModelId eq AvailableModels.id },
                ).joinCollectedContent()
        val contentMatched = hits[hitContentMatched]
        val columns =
            SearchColumns.withKeyword(
                keyword = keyword.value,
                pattern = pattern,
                isMetadataMatched = hits[hitIsMetadataMatched],
                contentMatched = contentMatched,
                contentScore =
                    if (order == LinkSearchOrder.SIMILAR && keyword.includesBody) {
                        // 본문 불일치 행은 0(LEAST(NULL, 1) = 1 함정 회피).
                        Case()
                            .When(
                                contentMatched,
                                leastDouble(similarity(lowerText(LinkContents.contentText), keyword.value), doubleLiteral(1.0)),
                            ).Else(doubleLiteral(0.0))
                    } else {
                        doubleLiteral(0.0)
                    },
            )

        return selectPage(
            joined = joined,
            columns = columns,
            condition = Op.TRUE,
            sortKeys = sortKeys(order = order, columns = columns, hasKeyword = true),
            cursor = cursor,
            size = size,
        )
    }

    private fun contentCandidates(
        ownerId: Long,
        accessible: Op<Boolean>,
        pattern: ExpressionWithColumnType<String>,
    ): Query =
        LinkContents
            .join(
                otherTable = Links,
                joinType = JoinType.INNER,
                onColumn = LinkContents.linkId,
                otherColumn = Links.id,
            ).join(
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
            ).select(
                LinkContents.linkId.alias("link_id"),
                booleanLiteral(false).alias("meta_hit"),
                booleanLiteral(true).alias("content_hit"),
            ).where {
                accessible and
                    (LinkContents.status eq ContentStatus.COLLECTED) and
                    (lowerText(LinkContents.contentText) like pattern)
            }

    private fun selectPage(
        joined: ColumnSet,
        columns: SearchColumns,
        condition: Op<Boolean>,
        sortKeys: List<SortKey>,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery> {
        val folderName = Folders.name.alias("folder_name")
        val folderEmoji = Folders.emoji.alias("folder_emoji")
        val workModel = AvailableModels.model.alias("work_model")

        return joined
            .select(
                listOf(
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
                    LinkContents.excerpt,
                ) + columns.aliases,
            ).where {
                condition and (cursor?.value?.let { keysetAfter(sortKeys, it) } ?: Op.TRUE)
            }.orderBy(*sortKeys.map { it.expression to it.sortOrder }.toTypedArray())
            .limit(size + 1)
            .map { row ->
                row.toSearchLinksQuery(
                    folderName = folderName,
                    folderEmoji = folderEmoji,
                    workModel = workModel,
                    columns = columns,
                )
            }
    }

    /**
     * 본문 일치 링크마다 일치어 앞 문맥 ≤15 코드포인트부터 ≤96 코드포인트 창을 DB에서 자른다. 본문 전체는 읽지 않는다.
     * 본문 길이도 창과 같은 행·같은 문장에서 읽는다(페이지 쿼리 뒤 본문이 바뀌어도 창과 예산이 어긋나지 않게).
     */
    private fun findSnippets(
        linkIds: List<Long>,
        keyword: String,
    ): Map<Long, LinkSnippetQuery> {
        if (linkIds.isEmpty()) {
            return emptyMap()
        }

        val position = textPosition(lowerText(LinkContents.contentText), keyword)
        val start = greatestInt(position.minus(Snippet.CONTEXT_BEFORE), intLiteral(1))
        val source = Substring(LinkContents.contentText, start, intLiteral(Snippet.SOURCE_MAX_CODE_POINTS)).alias("snippet_source")
        val headCut = boolOf(GreaterOp(start, intLiteral(1))).alias("snippet_head_cut")
        val tailCut =
            boolOf(LessOp(start.plus(Snippet.SOURCE_MAX_CODE_POINTS - 1), LinkContents.contentText.charLength()))
                .alias("snippet_tail_cut")

        return LinkContents
            .select(LinkContents.linkId, LinkContents.contentLength, source, headCut, tailCut)
            .where {
                (LinkContents.linkId inList linkIds) and
                    (LinkContents.status eq ContentStatus.COLLECTED) and
                    GreaterOp(position, intLiteral(0))
            }.mapNotNull { row ->
                row[LinkContents.contentLength]?.let { bodyLength ->
                    LinkSnippetQuery(
                        linkId = row[LinkContents.linkId],
                        source = row[source],
                        headCut = row[headCut],
                        tailCut = row[tailCut],
                        bodyLength = bodyLength,
                    )
                }
            }.associateBy { it.linkId }
    }

    private fun accessibleLinks(ownerId: Long): Join =
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
            )

    /** 내 링크 + 지금 멤버인 공유 폴더의 링크, 그리고 폴더·즐겨찾기 필터. [accessibleLinks] 조인이 필요하다. */
    private fun accessibleWith(
        ownerId: Long,
        folderId: Long?,
        isFavorite: Boolean?,
    ): Op<Boolean> =
        listOfNotNull(
            (Links.ownerId eq ownerId) or ((FolderMembers.userId eq ownerId) and Folders.sharedAt.isNotNull()),
            folderId?.let { requestedFolderId ->
                when (requestedFolderId) {
                    0L -> Links.folderId.isNull()
                    else -> Links.folderId eq requestedFolderId
                }
            },
            isFavorite?.let { favorite ->
                when (favorite) {
                    true -> Links.favoriteAt.isNotNull()
                    false -> Links.favoriteAt.isNull()
                }
            },
        ).reduce { left, right -> left and right }

    private fun Join.joinCollectedContent(): Join =
        join(
            otherTable = LinkContents,
            joinType = JoinType.LEFT,
            additionalConstraint = {
                (LinkContents.linkId eq Links.id) and
                    (LinkContents.status eq ContentStatus.COLLECTED)
            },
        )

    /**
     * 정렬 키 튜플. 검색어가 있으면 메타 일치 여부가 첫 키다.
     * 커서 값이 없을 때의 기본값은 기존 커서 비교식과 같다.
     */
    private fun sortKeys(
        order: LinkSearchOrder,
        columns: SearchColumns,
        hasKeyword: Boolean,
    ): List<SortKey> {
        val metadataKey =
            SortKey(boolToInt(columns.isMetadataMatched), SortOrder.DESC) { intLiteral(if (it.isMetadataMatched == true) 1 else 0) }
        val orderKeys =
            when (order) {
                LinkSearchOrder.LATEST -> {
                    listOf(
                        SortKey(
                            Links.createdAt,
                            SortOrder.DESC,
                        ) { QueryParameter(it.createdAt!!.toJavaInstant(), Links.createdAt.columnType) },
                        SortKey(Links.id, SortOrder.DESC) { longLiteral(it.id!!) },
                    )
                }

                LinkSearchOrder.EARLIEST -> {
                    listOf(
                        SortKey(
                            Links.createdAt,
                            SortOrder.ASC,
                        ) { QueryParameter(it.createdAt!!.toJavaInstant(), Links.createdAt.columnType) },
                        SortKey(Links.id, SortOrder.ASC) { longLiteral(it.id!!) },
                    )
                }

                LinkSearchOrder.LAXICO -> {
                    listOf(
                        SortKey(Links.title, SortOrder.ASC) { QueryParameter(it.title ?: "", Links.title.columnType) },
                        SortKey(Links.id, SortOrder.ASC) { longLiteral(it.id ?: Long.MIN_VALUE) },
                    )
                }

                LinkSearchOrder.SIMILAR -> {
                    listOfNotNull(
                        SortKey(columns.score, SortOrder.DESC) { doubleLiteral(it.score ?: Double.MIN_VALUE) },
                        SortKey(columns.titleScore, SortOrder.DESC) { doubleLiteral(it.titleScore ?: Double.MAX_VALUE) },
                        SortKey(columns.urlScore, SortOrder.DESC) { doubleLiteral(it.urlScore ?: Double.MAX_VALUE) },
                        SortKey(columns.tagsScore, SortOrder.DESC) { doubleLiteral(it.tagsScore ?: Double.MAX_VALUE) },
                        SortKey(columns.summaryScore, SortOrder.DESC) { doubleLiteral(it.summaryScore ?: Double.MAX_VALUE) },
                        SortKey(columns.memoScore, SortOrder.DESC) { doubleLiteral(it.memoScore ?: Double.MAX_VALUE) },
                        if (hasKeyword) {
                            SortKey(columns.contentScore, SortOrder.DESC) { doubleLiteral(it.contentScore ?: Double.MAX_VALUE) }
                        } else {
                            null
                        },
                        SortKey(Links.id, SortOrder.DESC) { longLiteral(it.id ?: Long.MAX_VALUE) },
                    )
                }
            }

        return if (hasKeyword) listOf(metadataKey) + orderKeys else orderKeys
    }

    /** 커서 행 뒤의 행: 앞 키가 같으면 다음 키로 넘어가는 사전순 비교(`k1 > c1 OR (k1 = c1 AND (k2 > c2 OR …))`). */
    private fun keysetAfter(
        keys: List<SortKey>,
        cursorValue: LinkSearchCursorValue,
    ): Op<Boolean> =
        keys.dropLast(1).foldRight(keys.last().beyond(cursorValue)) { key, rest ->
            key.beyond(cursorValue) or (EqOp(key.expression, key.cursorValueOf(cursorValue)) and rest)
        }

    private class SortKey(
        val expression: Expression<*>,
        val sortOrder: SortOrder,
        val cursorValueOf: (LinkSearchCursorValue) -> Expression<*>,
    ) {
        fun beyond(cursorValue: LinkSearchCursorValue): Op<Boolean> =
            when (sortOrder) {
                SortOrder.DESC -> LessOp(expression, cursorValueOf(cursorValue))
                else -> GreaterOp(expression, cursorValueOf(cursorValue))
            }
    }

    /** 검색 결과의 일치·점수 컬럼. 검색어가 없으면 모두 거짓·0이다. */
    private class SearchColumns(
        isMetadataMatched: ExpressionWithColumnType<Boolean>,
        contentMatched: ExpressionWithColumnType<Boolean>,
        titleMatched: ExpressionWithColumnType<Boolean>,
        urlMatched: ExpressionWithColumnType<Boolean>,
        tagsMatched: ExpressionWithColumnType<Boolean>,
        summaryMatched: ExpressionWithColumnType<Boolean>,
        memoMatched: ExpressionWithColumnType<Boolean>,
        val titleScore: ExpressionWithColumnType<Double>,
        val urlScore: ExpressionWithColumnType<Double>,
        val tagsScore: ExpressionWithColumnType<Double>,
        val summaryScore: ExpressionWithColumnType<Double>,
        val memoScore: ExpressionWithColumnType<Double>,
        val contentScore: ExpressionWithColumnType<Double>,
    ) {
        val isMetadataMatched: Expression<Boolean> = isMetadataMatched
        val score: ExpressionWithColumnType<Double> =
            titleScore.times(doubleLiteral(5.0)) +
                urlScore.times(doubleLiteral(3.0)) +
                tagsScore.times(doubleLiteral(2.0)) +
                summaryScore.times(doubleLiteral(1.0)) +
                memoScore.times(doubleLiteral(0.5)) +
                contentScore.times(doubleLiteral(0.3))

        val isMetadataMatchedAlias = isMetadataMatched.alias("is_metadata_matched")
        val contentMatchedAlias = contentMatched.alias("is_content_matched")
        val titleMatchedAlias = titleMatched.alias("title_matched")
        val urlMatchedAlias = urlMatched.alias("url_matched")
        val tagsMatchedAlias = tagsMatched.alias("tags_matched")
        val summaryMatchedAlias = summaryMatched.alias("summary_matched")
        val memoMatchedAlias = memoMatched.alias("memo_matched")
        val scoreAlias = score.alias("score")
        val titleScoreAlias = titleScore.alias("title_score")
        val urlScoreAlias = urlScore.alias("url_score")
        val tagsScoreAlias = tagsScore.alias("tags_score")
        val summaryScoreAlias = summaryScore.alias("summary_score")
        val memoScoreAlias = memoScore.alias("memo_score")
        val contentScoreAlias = contentScore.alias("content_score")

        val aliases: List<Expression<*>> =
            listOf(
                isMetadataMatchedAlias,
                contentMatchedAlias,
                titleMatchedAlias,
                urlMatchedAlias,
                tagsMatchedAlias,
                summaryMatchedAlias,
                memoMatchedAlias,
                scoreAlias,
                titleScoreAlias,
                urlScoreAlias,
                tagsScoreAlias,
                summaryScoreAlias,
                memoScoreAlias,
                contentScoreAlias,
            )

        companion object {
            fun withoutKeyword(): SearchColumns =
                SearchColumns(
                    isMetadataMatched = booleanLiteral(false),
                    contentMatched = booleanLiteral(false),
                    titleMatched = booleanLiteral(false),
                    urlMatched = booleanLiteral(false),
                    tagsMatched = booleanLiteral(false),
                    summaryMatched = booleanLiteral(false),
                    memoMatched = booleanLiteral(false),
                    titleScore = doubleLiteral(0.0),
                    urlScore = doubleLiteral(0.0),
                    tagsScore = doubleLiteral(0.0),
                    summaryScore = doubleLiteral(0.0),
                    memoScore = doubleLiteral(0.0),
                    contentScore = doubleLiteral(0.0),
                )

            fun withKeyword(
                keyword: String,
                pattern: ExpressionWithColumnType<String>,
                isMetadataMatched: ExpressionWithColumnType<Boolean>,
                contentMatched: ExpressionWithColumnType<Boolean>,
                contentScore: ExpressionWithColumnType<Double>,
            ): SearchColumns =
                SearchColumns(
                    isMetadataMatched = isMetadataMatched,
                    contentMatched = contentMatched,
                    titleMatched = boolOf(matches(Links.title, pattern)),
                    urlMatched = boolOf(matches(Links.url, pattern)),
                    tagsMatched = boolOf(matches(TAGS_TEXT, pattern)),
                    summaryMatched = boolOf(matches(SUMMARY_TEXT, pattern)),
                    memoMatched = boolOf(matches(MEMO_TEXT, pattern)),
                    titleScore = similarity(Links.title, keyword),
                    urlScore = similarity(Links.url, keyword),
                    tagsScore = similarity(TAGS_TEXT, keyword),
                    summaryScore = similarity(SUMMARY_TEXT, keyword),
                    memoScore = similarity(MEMO_TEXT, keyword),
                    contentScore = contentScore,
                )
        }
    }

    private fun ResultRow.toSearchLinksQuery(
        folderName: Expression<String>,
        folderEmoji: Expression<String?>,
        workModel: Expression<String>,
        columns: SearchColumns,
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
            score = this[columns.scoreAlias],
            titleScore = this[columns.titleScoreAlias],
            urlScore = this[columns.urlScoreAlias],
            tagsScore = this[columns.tagsScoreAlias],
            summaryScore = this[columns.summaryScoreAlias],
            memoScore = this[columns.memoScoreAlias],
            contentExcerpt = this[LinkContents.excerpt],
            isMetadataMatched = this[columns.isMetadataMatchedAlias],
            titleMatched = this[columns.titleMatchedAlias],
            urlMatched = this[columns.urlMatchedAlias],
            tagsMatched = this[columns.tagsMatchedAlias],
            summaryMatched = this[columns.summaryMatchedAlias],
            memoMatched = this[columns.memoMatchedAlias],
            contentMatched = this[columns.contentMatchedAlias],
            contentScore = this[columns.contentScoreAlias],
        )

    private fun ResultRow.toLinkDetailQuery(
        folderName: Expression<String>,
        folderEmoji: Expression<String?>,
        workModel: Expression<String>,
        hasContent: Expression<Boolean>,
        contentExcerpt: Expression<String?>,
        isContentPending: Expression<Boolean>,
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
            hasContent = this[hasContent],
            contentExcerpt = this[contentExcerpt],
            isContentPending = this[isContentPending],
        )
}

private val TAGS_TEXT = arrayToString(Links.tags, " ")
private val SUMMARY_TEXT = coalesceText(Links.summary)
private val MEMO_TEXT = coalesceText(Links.memo)

/** 메타데이터 일치를 보는 필드(제목·URL·태그·요약·메모). */
private val METADATA_FIELDS: List<Expression<*>> = listOf(Links.title, Links.url, TAGS_TEXT, SUMMARY_TEXT, MEMO_TEXT)

/**
 * pg_bigm 유사도를 double precision으로 읽는다. `bigm_similarity`는 real을 돌려주는데, Exposed는 Float을 문자열을 거쳐
 * Double로 바꿔(0.5555556f → 0.5555556) 커서에 담긴 값이 DB 값과 같지 않게 된다. 그러면 커서 경계 행이 다음 페이지에 다시 나온다.
 */
private fun similarity(
    field: Expression<*>,
    keyword: String,
): ExpressionWithColumnType<Double> = bigmSimilarity(field, keyword).castTo(DoubleColumnType())

private fun matches(
    field: Expression<*>,
    pattern: ExpressionWithColumnType<String>,
): Op<Boolean> = lowerText(field) like pattern
