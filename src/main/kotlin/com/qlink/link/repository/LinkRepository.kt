package com.qlink.link.repository

import com.qlink.link.domain.Link
import com.qlink.link.domain.LinkSort
import com.qlink.link.domain.LinkStatus
import com.qlink.link.domain.SearchKeyword
import com.qlink.link.dto.LinkDetailQuery
import com.qlink.link.dto.LinkSearchCursor
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
        keyword: SearchKeyword?,
        folderId: Long?,
        isFavorite: Boolean?,
        sort: LinkSort,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery>

    suspend fun update(link: Link): Link

    suspend fun updateFavorite(
        linkId: Long,
        favoriteAt: Instant?,
    )

    suspend fun updatePatchFields(
        linkId: Long,
        folderId: Long?,
        memo: String?,
        tags: List<String>,
    ): Link

    suspend fun updateAiResult(
        linkId: Long,
        folderId: Long?,
        title: String,
        summary: String?,
        tags: List<String>,
        status: LinkStatus,
        workModelId: Long?,
    )

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
