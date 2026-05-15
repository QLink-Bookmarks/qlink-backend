package support

import com.qlink.common.transaction.TransactionRunner
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

class TestTransactionRunner(
    private val database: Database,
) : TransactionRunner {
    override suspend fun <T> required(block: suspend () -> T): T =
        suspendTransaction(db = database, readOnly = false) {
            block()
        }

    override suspend fun <T> readOnly(block: suspend () -> T): T =
        suspendTransaction(db = database, readOnly = true) {
            block()
        }
}
