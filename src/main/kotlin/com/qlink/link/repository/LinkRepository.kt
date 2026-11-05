package com.qlink.link.repository

import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkStatus
import com.qlink.link.dto.LinkDetailQuery
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.dto.SearchLinksQuery
import kotlin.time.Instant

interface LinkRepository {
    suspend fun insert(link: Link): Link

    suspend fun insertAll(links: List<Link>): List<Link>

    suspend fun findById(linkId: Long): Link?

    suspend fun findByIdForUpdate(linkId: Long): Link?

    suspend fun findByIdForShare(linkId: Long): Link?

    suspend fun findAllByFolderIdForUpdate(folderId: Long): List<Link>

    suspend fun findAllByIdsForUpdate(linkIds: List<Long>): List<Link>

    suspend fun assignFolderByIds(
        linkIds: List<Long>,
        folderId: Long?,
        now: Instant,
    )

    suspend fun findAllByIds(linkIds: List<Long>): List<Link>

    suspend fun findAllByFolderIds(folderIds: List<Long>): List<Link>

    suspend fun findAllUrlsByOwnerId(ownerId: Long): List<String>

    suspend fun findCreatedAtById(linkId: Long): Instant?

    suspend fun findDetailById(
        linkId: Long,
        loginId: Long,
    ): LinkDetailQuery?

    suspend fun search(
        ownerId: Long,
        query: String?,
        folderId: Long?,
        isFavorite: Boolean?,
        order: LinkSearchOrder,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery>

    suspend fun update(link: Link): Link

    /** 즐겨찾기 시각만 쓴다. URL·검색 텍스트는 건드리지 않는다. */
    suspend fun updateFavorite(
        linkId: Long,
        favoriteAt: Instant?,
    )

    /** 폴더·메모·태그만 쓰고, 검색 텍스트는 이 값들과 현재 제목·URL·요약 컬럼으로 SQL에서 다시 만든다. */
    suspend fun updatePatchFields(
        linkId: Long,
        folderId: Long?,
        memo: String?,
        tags: List<String>,
    ): Link

    /** AI 요약 결과(폴더·제목·요약·태그·상태·모델)만 쓰고, 검색 텍스트는 현재 URL·메모 컬럼과 함께 SQL에서 다시 만든다. */
    suspend fun updateAiResult(
        linkId: Long,
        folderId: Long?,
        title: String,
        summary: String?,
        tags: List<String>,
        status: LinkStatus,
        workModelId: Long?,
    )

    /** AI 요약 실패(제목·요약·상태)만 쓰고, 검색 텍스트는 현재 URL·태그·메모 컬럼과 함께 SQL에서 다시 만든다. */
    suspend fun updateAiFailure(
        linkId: Long,
        title: String,
        summary: String?,
        status: LinkStatus,
    )

    suspend fun updatePreview(link: Link): Boolean

    suspend fun detachFolder(folderId: Long)

    suspend fun detachFolders(folderIds: List<Long>)

    suspend fun deleteAllByFolderId(folderId: Long)

    suspend fun deleteById(linkId: Long)

    suspend fun deleteAllByIds(linkIds: List<Long>)
}
