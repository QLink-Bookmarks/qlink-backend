package support

import io.kotest.core.spec.style.FunSpec
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext

abstract class BaseServiceTest(
    body: FunSpec.() -> Unit,
) : FunSpec({
        beforeSpec {
            ServiceTestEnvironment.start()
            assumeTrue(ServiceTestEnvironment.isStarted)
        }

        aroundTest { (testCase, execute) ->
            suspendTransaction(db = GlobalContext.get().get<Database>()) {
                val result = execute(testCase)
                rollback()
                result
            }
        }

        body()
    }),
    KoinComponent
