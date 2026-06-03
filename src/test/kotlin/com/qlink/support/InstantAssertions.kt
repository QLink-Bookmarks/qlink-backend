package com.qlink.support

import java.time.temporal.ChronoUnit
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

fun Instant?.truncatedToSecond(): Instant? =
    this
        ?.toJavaInstant()
        ?.truncatedTo(ChronoUnit.SECONDS)
        ?.toKotlinInstant()
