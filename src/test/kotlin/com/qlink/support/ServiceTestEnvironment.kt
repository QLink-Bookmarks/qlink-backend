package com.qlink.support

import com.qlink.common.transaction.TransactionRunner
import com.qlink.config.DataSourceConfig
import com.qlink.di.dataModule
import com.qlink.di.notificationModule
import com.qlink.di.repositoryModule
import com.qlink.di.serviceModule
import com.qlink.di.transactionModule
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

private class ServicePostgreSQLContainer(
    imageName: DockerImageName,
) : PostgreSQLContainer<ServicePostgreSQLContainer>(imageName)

object ServiceTestEnvironment {
    private const val SCHEMA = "qlink_local"
    private const val DRIVER_CLASS_NAME = "org.postgresql.Driver"

    private val container =
        ServicePostgreSQLContainer(
            DockerImageName
                .parse("dungvti/postgres-bigm:18-alpine")
                .asCompatibleSubstituteFor("postgres"),
        ).withDatabaseName("qlink")
            .withUsername("local")
            .withPassword("1234")

    private var containerStarted = false

    val isStarted: Boolean
        get() = containerStarted && isKoinUsable()

    val jdbcUrl: String
        get() = container.currentSchemaJdbcUrl()

    val username: String
        get() = container.username

    val password: String
        get() = container.password

    val databaseName: String
        get() = container.databaseName

    fun start() {
        if (!containerStarted) {
            container.start()
            containerStarted = true
        }

        if (!isKoinUsable()) {
            startKoinContext()
            GlobalContext.get().get<Flyway>().migrate()
        }
    }

    fun stop() {
        if (!containerStarted) {
            return
        }

        runCatching {
            GlobalContext.get().getOrNull<DataSource>()
        }.getOrNull()
            ?.let { it as? HikariDataSource }
            ?.close()

        stopKoin()
        container.stop()
        containerStarted = false
    }

    fun applicationConfig(flywayLocations: List<String> = listOf("db/migration")): ApplicationConfig {
        start()

        val overrides =
            MapApplicationConfig().apply {
                put("ktor.deployment.port", "0")
                put("dataSource.jdbcUrl", jdbcUrl)
                put("dataSource.username", username)
                put("dataSource.password", password)
                put("dataSource.driverClassName", DRIVER_CLASS_NAME)
                put("flyway.schema", SCHEMA)
                put("flyway.locations", flywayLocations)
            }

        return OverlayApplicationConfig(
            overrides = overrides,
            fallback = requireNotNull(YamlConfig("application.yaml")),
        )
    }

    private fun startKoinContext() {
        runCatching {
            stopKoin()
        }

        startKoin {
            modules(
                dataModule(testApplicationConfig(), testDataSourceConfig()),
                transactionModule(),
                repositoryModule(),
                notificationModule(LoggerFactory.getLogger("TestTaskScheduler")),
                aiTestModule(),
                serviceModule(),
            )
        }
    }

    private fun isKoinUsable(): Boolean =
        runCatching {
            GlobalContext.get().get<DataSource>()
        }.isSuccess

    private fun testDataSourceConfig(): DataSourceConfig =
        DataSourceConfig(
            jdbcUrl = jdbcUrl,
            username = username,
            password = password,
            driverClassName = DRIVER_CLASS_NAME,
        )

    private fun testApplicationConfig(): MapApplicationConfig =
        MapApplicationConfig().apply {
            put("flyway.schema", SCHEMA)
            put("flyway.locations", listOf("db/migration"))
        }

    private fun ServicePostgreSQLContainer.currentSchemaJdbcUrl(): String {
        val separator = if (jdbcUrl.contains("?")) "&" else "?"
        return "$jdbcUrl${separator}currentSchema=$SCHEMA"
    }
}

inline fun <reified T : Any> koinGet(): T = GlobalContext.get().get()

suspend fun <T> TransactionRunner.rollback(
    database: Database,
    block: suspend () -> T,
): T =
    withContext(Dispatchers.IO) {
        suspendTransaction(db = database, readOnly = false) {
            try {
                block()
            } finally {
                rollback()
            }
        }
    }

private class OverlayApplicationConfig(
    private val overrides: ApplicationConfig,
    private val fallback: ApplicationConfig,
) : ApplicationConfig {
    override fun property(path: String) = propertyOrNull(path) ?: fallback.property(path)

    override fun propertyOrNull(path: String) = overrides.propertyOrNull(path) ?: fallback.propertyOrNull(path)

    override fun config(path: String): ApplicationConfig = runCatching { overrides.config(path) }.getOrElse { fallback.config(path) }

    override fun configList(path: String): List<ApplicationConfig> =
        runCatching { overrides.configList(path) }.getOrElse { fallback.configList(path) }

    override fun keys(): Set<String> = overrides.keys() + fallback.keys()

    override fun toMap(): Map<String, Any> = (fallback.toMap() + overrides.toMap()).mapValues { requireNotNull(it.value) }
}
