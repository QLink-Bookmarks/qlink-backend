package com.qlink.support

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

fun dbNow(): Instant =
    requireNotNull(
        TransactionManager.current().exec("SELECT LOCALTIMESTAMP") { result ->
            result.next()
            result.getTimestamp(1).toInstant().toKotlinInstant()
        },
    )
