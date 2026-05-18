package com.qlink

import com.qlink.support.ServiceTestEnvironment
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.sql.DriverManager

class PostgresContainerTest :
    StringSpec({
        "PostgreSQL testcontainer responds to a simple query" {
            ServiceTestEnvironment.start()

            DriverManager
                .getConnection(
                    ServiceTestEnvironment.jdbcUrl,
                    ServiceTestEnvironment.username,
                    ServiceTestEnvironment.password,
                ).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT current_database()").use { resultSet ->
                            resultSet.next() shouldBe true
                            resultSet.getString(1) shouldBe ServiceTestEnvironment.databaseName
                        }
                    }
                }
        }
    })
