package com.qlink.di

import com.qlink.config.DataSourceConfig
import com.qlink.config.FlywayConfig
import com.qlink.config.string
import com.qlink.config.stringList
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import javax.sql.DataSource

fun dataModule(
    config: ApplicationConfig,
    dataSourceConfig: DataSourceConfig? = null,
) = module {
    single {
        FlywayConfig(
            schema = config.string("flyway.schema"),
            locations = config.stringList("flyway.locations"),
        )
    }

    single {
        val resolvedDataSourceConfig =
            dataSourceConfig ?: run {
                val resolvedJdbcUrl = System.getenv("DB_JDBC_URL")?.takeUnless { it.isBlank() } ?: config.string("dataSource.jdbcUrl")
                val resolvedUsername =
                    System.getenv("DB_USERNAME")?.takeUnless { it.isBlank() } ?: config.string("dataSource.username")
                val resolvedPassword =
                    System.getenv("DB_PASSWORD")?.takeUnless { it.isBlank() } ?: config.string("dataSource.password")
                val resolvedDriverClassName =
                    System.getenv("DB_DRIVER_CLASS_NAME")?.takeUnless { it.isBlank() }
                        ?: config.string("dataSource.driverClassName")

                DataSourceConfig(
                    jdbcUrl = resolvedJdbcUrl,
                    username = resolvedUsername,
                    password = resolvedPassword,
                    driverClassName = resolvedDriverClassName,
                )
            }

        HikariConfig().apply {
            jdbcUrl = resolvedDataSourceConfig.jdbcUrl
            username = resolvedDataSourceConfig.username
            password = resolvedDataSourceConfig.password
            driverClassName = resolvedDataSourceConfig.driverClassName
            connectionInitSql = "SET TIME ZONE 'UTC'"
        }
    }

    single {
        HikariDataSource(get())
    }

    single<DataSource> {
        get<HikariDataSource>()
    }

    single {
        Database.connect(get<DataSource>())
    }

    single {
        val flywayConfig = get<FlywayConfig>()

        Flyway
            .configure()
            .dataSource(get<DataSource>())
            .schemas(flywayConfig.schema)
            .createSchemas(true)
            .locations(*flywayConfig.locations.toTypedArray())
            .load()
    }
}
