package com.qlink.common.logging

import kotlinx.coroutines.slf4j.MDCContext
import java.util.UUID

const val TRACE_ID_MDC_KEY = "traceId"

private const val MAX_TRACE_ID_LENGTH = 32

fun newTraceId(length: Int): String =
    UUID
        .randomUUID()
        .toString()
        .replace("-", "")
        .take(length.coerceIn(1, MAX_TRACE_ID_LENGTH))

fun traceContext(traceIdLength: Int): MDCContext = MDCContext(mapOf(TRACE_ID_MDC_KEY to newTraceId(traceIdLength)))
