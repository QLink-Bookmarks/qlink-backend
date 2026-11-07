package com.qlink.common.search

import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder

class SortCollated(
    private val expression: ExpressionWithColumnType<String>,
) : ExpressionWithColumnType<String>() {
    override val columnType: IColumnType<String> = expression.columnType

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder
            .append("(")
            .append(expression)
            .append(" COLLATE app_sort)")
    }
}
