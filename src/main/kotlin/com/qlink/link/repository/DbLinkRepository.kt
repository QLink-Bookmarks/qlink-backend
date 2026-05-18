package com.qlink.link.repository

import com.qlink.link.domain.Link
import com.qlink.link.repository.table.Links
import com.qlink.link.repository.table.fromDomain
import com.qlink.link.repository.table.refreshUpdatedAt
import com.qlink.link.repository.table.toLinkDomain
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class DbLinkRepository : LinkRepository {
    override suspend fun insert(link: Link): Link = Links.insertReturning { it.fromDomain(link) }.single().toLinkDomain()

    override suspend fun findById(linkId: Long): Link? =
        Links
            .selectAll()
            .where { Links.id eq linkId }
            .singleOrNull()
            ?.toLinkDomain()

    override suspend fun update(link: Link): Link? {
        val linkId = link.id ?: return null
        val updatedCount =
            Links.update({ Links.id eq linkId }) {
                it.fromDomain(link)
                it.refreshUpdatedAt()
            }

        if (updatedCount == 0) {
            return null
        }

        return findById(linkId)
    }
}
