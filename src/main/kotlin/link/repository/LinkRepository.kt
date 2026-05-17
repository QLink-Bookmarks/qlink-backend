package com.qlink.link.repository

import com.qlink.link.domain.Link

interface LinkRepository {
    suspend fun insert(link: Link): Link
    suspend fun findById(linkId: Long): Link?
}