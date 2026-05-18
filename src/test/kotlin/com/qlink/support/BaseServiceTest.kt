package com.qlink.support

import com.qlink.common.transaction.TransactionRunner
import io.kotest.core.spec.style.BehaviorSpec
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.koin.core.component.KoinComponent

abstract class BaseServiceTest(
    body: BehaviorSpec.() -> Unit,
) : BehaviorSpec({
        ServiceTestEnvironment.start()

        val tx = koinGet<TransactionRunner>()
        val db = koinGet<Database>()

        beforeSpec {
            ServiceTestEnvironment.start()
            assumeTrue(ServiceTestEnvironment.isStarted)
        }

        aroundTest { (testCase, execute) ->
            tx.rollback(db) {
                execute(testCase)
            }
        }

        body()
    }),
    KoinComponent
