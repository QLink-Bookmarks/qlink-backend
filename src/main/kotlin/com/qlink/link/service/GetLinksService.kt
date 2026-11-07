package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.scroll.Base64CursorCodec
import com.qlink.common.scroll.DEFAULT_SCROLL_SIZE
import com.qlink.common.scroll.ScrollRequest
import com.qlink.common.scroll.ScrollResponse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.link.domain.LinkOrderKey
import com.qlink.link.domain.LinkSort
import com.qlink.link.domain.MatchedField
import com.qlink.link.domain.SearchKeyword
import com.qlink.link.domain.Snippet
import com.qlink.link.dto.GetLinksContentResponse
import com.qlink.link.dto.HighlightRange
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchCursorValue
import com.qlink.link.dto.SearchLinksQuery
import com.qlink.link.dto.SnippetResponse
import com.qlink.link.dto.TodoCountsResponse
import com.qlink.link.repository.LinkRepository
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock
import kotlin.time.Instant

class GetLinksService(
    private val tx: TransactionRunner,
    private val linkRepository: LinkRepository,
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
) {
    suspend fun getLinks(
        loginId: Long,
        query: String?,
        folderId: Long?,
        isFavorite: Boolean? = null,
        orderKey: String? = null,
        order: String?,
        scrollRequest: ScrollRequest,
    ): ScrollResponse<GetLinksContentResponse> =
        tx.readOnly {
            userRepository.emptyById(loginId).requireFalse(ErrorCode.LINK_OWNER_NOT_FOUND)

            val sort = LinkSort.of(orderKey = orderKey, order = order)
            val keyword = SearchKeyword.of(query).takeUnless { it.isBlank }
            val cursor = scrollRequest.cursor?.let { decodeCursor(encoded = it, sort = sort, hasKeyword = keyword != null) }
            val size = scrollRequest.size.takeIf { it > 0 } ?: DEFAULT_SCROLL_SIZE
            val queries =
                linkRepository.search(
                    ownerId = loginId,
                    keyword = keyword,
                    folderId = folderId,
                    isFavorite = isFavorite,
                    sort = sort,
                    cursor = cursor,
                    size = size,
                )
            val hasNext = queries.size > size
            val contents = queries.take(size)
            val todosByLinkId = todoRepository.findAllByLinkIds(contents.map { it.id }).groupBy { it.linkId }
            val now = Clock.System.now()

            ScrollResponse(
                isEmpty = contents.isEmpty(),
                contents =
                    contents.map { queryRow ->
                        GetLinksContentResponse(
                            id = queryRow.id,
                            folderId = queryRow.folderId,
                            folderName = queryRow.folderName,
                            folderEmoji = queryRow.folderEmoji,
                            workModel = queryRow.workModel,
                            url = queryRow.url,
                            title = queryRow.title,
                            thumbnailUrl = queryRow.thumbnailUrl,
                            faviconUrl = queryRow.faviconUrl,
                            isVideo = queryRow.isVideo,
                            status = queryRow.status,
                            tags = queryRow.tags,
                            createdAt = queryRow.createdAt,
                            todoCounts = todosByLinkId[queryRow.id].orEmpty().toCounts(now),
                            isFavorite = queryRow.favoriteAt != null,
                            contentExcerpt = queryRow.contentExcerpt,
                            matchedFields = queryRow.matchedFields(),
                            contentSnippet = keyword?.let { queryRow.toSnippetResponse(it) },
                        )
                    },
                nextCursor =
                    contents
                        .lastOrNull()
                        ?.takeIf { hasNext }
                        ?.let { encodeCursor(query = it, sort = sort, hasKeyword = keyword != null) },
                hasNext = hasNext,
            )
        }

    private suspend fun decodeCursor(
        encoded: String,
        sort: LinkSort,
        hasKeyword: Boolean,
    ): LinkSearchCursor {
        val cursor = Base64CursorCodec.decode<LinkSearchCursor>(encoded)
        sort.validateCursor(orderKey = cursor.orderKey, direction = cursor.direction, order = cursor.order)
        validateCursorValue(value = cursor.value, orderKey = sort.orderKey, hasKeyword = hasKeyword)

        return cursor.withCreatedAt(sort.orderKey)
    }

    private fun validateCursorValue(
        value: LinkSearchCursorValue,
        orderKey: LinkOrderKey,
        hasKeyword: Boolean,
    ) {
        if (hasKeyword) {
            value.isMetadataMatched ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
            if (orderKey == LinkOrderKey.SIMILARITY) {
                value.contentScore ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
            }
        }

        when (orderKey) {
            LinkOrderKey.CREATED_AT -> {
                value.id ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
            }

            LinkOrderKey.TITLE -> {
                value.title ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
                value.id ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
            }

            LinkOrderKey.SIMILARITY -> {
                value.score ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
                value.id ?: throw BusinessException(ErrorCode.COMMON_CURSOR_FIELD_MISSING)
            }
        }
    }

    private suspend fun LinkSearchCursor.withCreatedAt(orderKey: LinkOrderKey): LinkSearchCursor =
        when {
            orderKey != LinkOrderKey.CREATED_AT || value.createdAt != null -> {
                this
            }

            else -> {
                linkRepository
                    .findCreatedAtById(value.id!!)
                    ?.let { copy(value = value.copy(createdAt = it)) }
                    ?: throw BusinessException(ErrorCode.COMMON_CURSOR_MALFORMED)
            }
        }

    private fun encodeCursor(
        query: SearchLinksQuery,
        sort: LinkSort,
        hasKeyword: Boolean,
    ): String =
        Base64CursorCodec.encode(
            LinkSearchCursor(
                orderKey = sort.orderKey,
                direction = sort.direction,
                value =
                    LinkSearchCursorValue(
                        id = query.id,
                        createdAt = query.createdAt,
                        title = query.title,
                        score = query.score,
                        titleScore = query.titleScore,
                        urlScore = query.urlScore,
                        tagsScore = query.tagsScore,
                        summaryScore = query.summaryScore,
                        memoScore = query.memoScore,
                        isMetadataMatched = query.isMetadataMatched.takeIf { hasKeyword },
                        contentScore = query.contentScore.takeIf { hasKeyword },
                    ),
            ),
        )

    private fun SearchLinksQuery.matchedFields(): List<MatchedField> =
        listOfNotNull(
            MatchedField.TITLE.takeIf { titleMatched },
            MatchedField.URL.takeIf { urlMatched },
            MatchedField.TAGS.takeIf { tagsMatched },
            MatchedField.SUMMARY.takeIf { summaryMatched },
            MatchedField.MEMO.takeIf { memoMatched },
            MatchedField.CONTENT.takeIf { contentMatched },
        )

    private fun SearchLinksQuery.toSnippetResponse(keyword: SearchKeyword): SnippetResponse? =
        snippet?.let { query ->
            val snippet =
                Snippet.of(
                    source = query.source,
                    headCut = query.headCut,
                    tailCut = query.tailCut,
                    keyword = keyword.value,
                    bodyLength = query.bodyLength,
                )
            SnippetResponse(
                text = snippet.text,
                highlights = snippet.highlights.map { HighlightRange(start = it.start, end = it.end) },
            )
        }

    private fun List<Todo>.toCounts(now: Instant): TodoCountsResponse =
        TodoCountsResponse(
            open = count { !it.isCompleted },
            dueSoon = count { it.isDueSoon(now) },
            overdue = count { it.isOverdue(now) },
            completed = count { it.isCompleted },
        )
}
