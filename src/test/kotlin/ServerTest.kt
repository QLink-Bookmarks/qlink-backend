package com.qlink

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import support.ServiceTestEnvironment

class ServerTest :
    StringSpec({
        "application context starts with PostgreSQL testcontainer" {
            testApplication {
                environment {
                    config = ServiceTestEnvironment.applicationConfig()
                }

                client.get("/").status shouldBe HttpStatusCode.OK
            }
        }
    })
