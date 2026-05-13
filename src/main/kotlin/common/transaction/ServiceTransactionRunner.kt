package com.qlink.common.transaction

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

class ServiceTransactionRunner(private val database: Database) : TransactionRunner {
  override suspend fun <T> required(block: suspend () -> T): T = withContext(Dispatchers.IO) {
    suspendTransaction(db = database, readOnly = false) {
      block()
    }
  }

  override suspend fun <T> readOnly(block: suspend () -> T): T = withContext(Dispatchers.IO) {
    suspendTransaction(db = database, readOnly = true) {
      block()
    }
  }
}
