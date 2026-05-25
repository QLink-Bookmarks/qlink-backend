package com.qlink.link.repository

import com.qlink.link.domain.Link
import com.qlink.link.dto.LinkSearchCursor
import com.qlink.link.dto.LinkSearchOrder
import com.qlink.link.dto.SearchLinksQuery

interface LinkRepository {
    suspend fun insert(link: Link): Link

    suspend fun findById(linkId: Long): Link?

    suspend fun search(
        ownerId: Long,
        query: String?,
        folderId: Long?,
        order: LinkSearchOrder,
        cursor: LinkSearchCursor?,
        size: Int,
    ): List<SearchLinksQuery>

    suspend fun update(link: Link): Link

    suspend fun deleteById(linkId: Long)
}
