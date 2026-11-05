package com.qlink.common.search

import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.booleanLiteral

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

/** pg_bigm `likequery`: `%`·`_`·역슬래시를 이스케이프하고 앞뒤에 `%`를 붙인 LIKE 패턴. 검색어를 글자 그대로 찾는다. */
fun likeQuery(keyword: String): CustomFunction<String> =
    CustomFunction(
        functionName = "public.likequery",
        columnType = TextColumnType(),
        textLiteral(keyword),
    )

/** 참이면 1, 아니면(NULL 포함) 0. 불린을 정렬·커서 비교 키로 쓸 때 쓴다. */
fun boolToInt(condition: Expression<Boolean>): ExpressionWithColumnType<Int> =
    Case()
        .When(condition, intLiteral(1))
        .Else(intLiteral(0))

/** 참이면 TRUE, 아니면(NULL 포함) FALSE. 조건식을 NULL 없는 불린 컬럼으로 투영할 때 쓴다. */
fun boolOf(condition: Expression<Boolean>): ExpressionWithColumnType<Boolean> =
    Case()
        .When(condition, booleanLiteral(true))
        .Else(booleanLiteral(false))

fun boolOr(expression: Expression<Boolean>): CustomFunction<Boolean> =
    CustomFunction(
        functionName = "bool_or",
        columnType = BooleanColumnType(),
        expression,
    )

/** `strpos(text, keyword)`: 1부터 센 코드포인트 위치, 없으면 0. */
fun textPosition(
    expression: Expression<*>,
    keyword: String,
): CustomFunction<Int> =
    CustomFunction(
        functionName = "strpos",
        columnType = IntegerColumnType(),
        expression,
        textLiteral(keyword),
    )

fun greatestInt(
    first: Expression<Int>,
    second: Expression<Int>,
): CustomFunction<Int> =
    CustomFunction(
        functionName = "GREATEST",
        columnType = IntegerColumnType(),
        first,
        second,
    )

fun leastDouble(
    first: Expression<Double>,
    second: Expression<Double>,
): CustomFunction<Double> =
    CustomFunction(
        functionName = "LEAST",
        columnType = DoubleColumnType(),
        first,
        second,
    )
