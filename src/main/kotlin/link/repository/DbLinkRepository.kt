package com.qlink.link.repository

import com.qlink.link.domain.Link
import com.qlink.link.repository.table.Links
import com.qlink.link.repository.table.fromDomain
import com.qlink.link.repository.table.toLinkDomain
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll

class DbLinkRepository() : LinkRepository {

    override suspend fun insert(link: Link): Link {
        return Links.insertReturning { it.fromDomain(link) }.single().toLinkDomain()
    }

    override suspend fun findById(linkId: Long): Link? {
        return Links.selectAll().where { Links.id eq linkId }.singleOrNull()?.toLinkDomain()
    }

}