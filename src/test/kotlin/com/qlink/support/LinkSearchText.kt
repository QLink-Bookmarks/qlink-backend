package com.qlink.support

import com.qlink.link.repository.table.Links
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

fun searchTextOf(linkId: Long): String =
    Links
        .select(Links.searchText)
        .where { Links.id eq linkId }
        .single()[Links.searchText]
