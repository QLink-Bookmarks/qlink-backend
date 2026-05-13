package com.qlink.common.transaction

interface TransactionRunner {
  suspend fun <T> required(block: suspend () -> T): T

  suspend fun <T> readOnly(block: suspend () -> T): T
}
