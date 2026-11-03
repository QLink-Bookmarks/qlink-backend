package com.qlink.common.search

import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.javatime.JavaInstantColumnType
import kotlin.time.Duration

class DbNowMinus(
    private val duration: Duration,
) : ExpressionWithColumnType<java.time.Instant?>() {
    override val columnType: IColumnType<java.time.Instant> = JavaInstantColumnType()

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("(now() - interval '${duration.inWholeSeconds} seconds')")
    }
}
