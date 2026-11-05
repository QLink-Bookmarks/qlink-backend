@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import com.qlink.common.search.SearchCursor
import com.qlink.common.search.SearchOrder
import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.SourceType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

const val DEFAULT_LINK_SEARCH_ORDER = "latest"

data class SearchLinksQuery(
    val id: Long,
    val folderId: Long?,
    val folderName: String?,
    val folderEmoji: String?,
    val workModel: String?,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val faviconUrl: String?,
    val isVideo: Boolean,
    val status: LinkStatus,
    val tags: List<String>,
    val createdAt: Instant,
    val favoriteAt: Instant?,
    val score: Double,
    val titleScore: Double,
    val urlScore: Double,
    val tagsScore: Double,
    val summaryScore: Double,
    val memoScore: Double,
    /** `COLLECTED`일 때의 발췌. 그 외 null. */
    val contentExcerpt: String?,
    val isMetadataMatched: Boolean,
    val titleMatched: Boolean,
    val urlMatched: Boolean,
    val tagsMatched: Boolean,
    val summaryMatched: Boolean,
    val memoMatched: Boolean,
    val contentMatched: Boolean,
    val contentScore: Double,
    /** 본문 일치 행에서 DB가 자른 창(일치어 앞 문맥 ≤15 + 나머지, ≤96 코드포인트). 본문 일치가 아니면 null. */
    val snippetSource: String? = null,
    val snippetHeadCut: Boolean = false,
    val snippetTailCut: Boolean = false,
    /** 스니펫 창을 자른 본문의 길이(UTF-16). 창과 같은 행·같은 문장에서 읽어 스니펫 예산에 쓴다. */
    val snippetBodyLength: Int? = null,
)

/** 본문 일치 링크의 스니펫 창. [SearchLinksQuery]의 스니펫 필드를 채운다. */
data class LinkSnippetQuery(
    val linkId: Long,
    val source: String,
    val headCut: Boolean,
    val tailCut: Boolean,
    val bodyLength: Int,
)

data class LinkDetailQuery(
    val id: Long,
    val ownerId: Long,
    val folderId: Long?,
    val folderSharedAt: Instant?,
    val folderMemberUserId: Long?,
    val folderName: String?,
    val folderEmoji: String?,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val faviconUrl: String?,
    val isVideo: Boolean,
    val summary: String?,
    val memo: String?,
    val tags: List<String>,
    val sourceType: SourceType,
    val status: LinkStatus,
    val workModelId: Long?,
    val workModel: String?,
    val createdAt: Instant,
    val favoriteAt: Instant?,
    /** 본문 행이 있는지(SQL `link_contents.id IS NOT NULL`). */
    val hasContent: Boolean,
    /** `COLLECTED`일 때의 발췌(SQL CASE). 그 외 null. */
    val contentExcerpt: String?,
    /** 본문 행이 `PENDING`인지(SQL). */
    val isContentPending: Boolean,
)

typealias LinkSearchOrder = SearchOrder

typealias LinkSearchCursor = SearchCursor<LinkSearchCursorValue>

@Serializable
data class LinkSearchCursorValue(
    val id: Long? = null,
    val createdAt: Instant? = null,
    val title: String? = null,
    val score: Double? = null,
    val titleScore: Double? = null,
    val urlScore: Double? = null,
    val tagsScore: Double? = null,
    val summaryScore: Double? = null,
    val memoScore: Double? = null,
    /** 검색어가 있을 때만: 메타데이터 일치 묶음(정렬 첫 키). */
    val isMetadataMatched: Boolean? = null,
    /** 검색어가 있을 때만: 본문 유사도(similar 정렬 키). */
    val contentScore: Double? = null,
)
