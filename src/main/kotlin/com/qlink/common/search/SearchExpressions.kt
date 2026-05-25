package com.qlink.common.search

import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.TextColumnType

fun intLiteral(value: Int): QueryParameter<Int> = QueryParameter(value, IntegerColumnType())

fun longLiteral(value: Long): QueryParameter<Long> = QueryParameter(value, LongColumnType())

fun doubleLiteral(value: Double): QueryParameter<Double> = QueryParameter(value, DoubleColumnType())

fun textLiteral(value: String): QueryParameter<String> = QueryParameter(value, TextColumnType())

fun coalesceText(expression: Expression<*>): CustomFunction<String> =
    CustomFunction(
        functionName = "COALESCE",
        columnType = TextColumnType(),
        expression,
        textLiteral(""),
    )

fun coalesceInt(expression: Expression<*>): CustomFunction<Int> =
    CustomFunction(
        functionName = "COALESCE",
        columnType = IntegerColumnType(),
        expression,
        intLiteral(0),
    )

fun lowerText(expression: Expression<*>): CustomFunction<String> =
    CustomFunction(
        functionName = "LOWER",
        columnType = TextColumnType(),
        expression,
    )

fun arrayToString(
    expression: Expression<*>,
    delimiter: String,
): CustomFunction<String> =
    CustomFunction(
        functionName = "array_to_string",
        columnType = TextColumnType(),
        expression,
        textLiteral(delimiter),
    )

fun bigmSimilarity(
    expression: Expression<*>,
    keyword: String,
): CustomFunction<Double> =
    CustomFunction(
        functionName = "public.bigm_similarity",
        columnType = DoubleColumnType(),
        expression,
        textLiteral(keyword),
    )
