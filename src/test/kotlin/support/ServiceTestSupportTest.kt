package support

import com.qlink.common.transaction.TransactionRunner
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.koin.core.context.GlobalContext
import javax.sql.DataSource

class ServiceTestSupportTest :
    BaseServiceTest({
        test("service test context exposes required database beans") {
            koinGet<DataSource>().connection.use { connection ->
                connection.isValid(1) shouldBe true
            }

            koinGet<Flyway>().configuration.locations.map { it.descriptor } shouldBe
                listOf("classpath:db/migration")
            koinGet<Database>().url.startsWith("jdbc:postgresql://") shouldBe true
            koinGet<TransactionRunner>()::class shouldBe TestTransactionRunner::class
        }

        test("flyway migration creates application tables") {
            tableExists("users") shouldBe true
            tableExists("links") shouldBe true
        }

        test("base service test transaction rolls back rows after each test") {
            userCount() shouldBe 0

            koinGet<TransactionRunner>().required {
                insertUser()
            }

            userCount() shouldBe 1
        }

        test("base service test transaction keeps the next test isolated") {
            userCount() shouldBe 0
        }

        test("test transaction runner readOnly runs queries") {
            koinGet<TransactionRunner>().readOnly {
                userCount()
            } shouldBe 0
        }
    })

suspend fun tableExists(tableName: String): Boolean =
    koinGet<TransactionRunner>().readOnly {
        currentJdbcTransaction().exec(
            """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = current_schema()
                AND table_name = '$tableName'
            )
            """.trimIndent(),
        ) { resultSet ->
            resultSet.next()
            resultSet.getBoolean(1)
        } ?: false
    }

suspend fun insertUser() {
    koinGet<TransactionRunner>().required {
        currentJdbcTransaction().exec(
            """
            INSERT INTO users (display_name, avatar_url, avatar_emoji, created_at, updated_at)
            VALUES ('service-test-user', NULL, NULL, now(), now())
            """.trimIndent(),
        )
    }
}

suspend fun userCount(): Long =
    koinGet<TransactionRunner>().readOnly {
        currentJdbcTransaction().exec("SELECT COUNT(*) FROM users") { resultSet ->
            resultSet.next()
            resultSet.getLong(1)
        } ?: 0
    }

private inline fun <reified T : Any> koinGet(): T = GlobalContext.get().get()

@OptIn(InternalApi::class)
private fun currentJdbcTransaction(): JdbcTransaction = currentTransaction() as JdbcTransaction
