package com.qlink.di

import com.qlink.common.string
import com.qlink.common.stringList
import com.qlink.config.DataSourceConfig
import com.qlink.config.FlywayConfig
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
            dataSourceConfig ?: DataSourceConfig(
                jdbcUrl = config.string("dataSource.jdbcUrl"),
                username = config.string("dataSource.username"),
                password = config.string("dataSource.password"),
                driverClassName = config.string("dataSource.driverClassName"),
            )

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
