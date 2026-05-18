# AGENTS.md

Guidance for agents working in this repository. Keep this file focused on project-specific
conventions that are not obvious from reading the code.

## Baseline

- Prefer Kotlin style logics.
- Readability, Simpleness, and Maintenance are the priorities.
- Prefer Tell, rather than Ask. Cohere boundary and context.

## Testing Rules

- Prioritize tests for the same areas included in Jacoco coverage: `domain/**` and `service/**`.
  Never create out-of-scope tests for other packages.
- Use Kotest `BehaviorSpec` as the default test style.
- Structure tests with `Given` as the shared logical context, and each `When` + `Then` pair as one
  scenario.
- Use fixtures and randomized data for all values except the method under test and the specific
  condition being tested.
- Give fixture functions meaningful names so tests stay reusable and readable.
- Domain tests should verify pure domain logic without external dependencies.
- Service tests must extend `BaseServiceTest` and use Koin DI through `support.koinGet<T>()`.
- In service tests, prepare required persisted data in `beforeTest` using fixtures and repositories.
- In service test `Then` blocks, verify behavior by querying the database through repositories.
- For intended exception scenarios, test only `BusinessException`.
- For tests of Unit-returning-fun, define an action lambda in `When`, then assert with
  `shouldNotThrow` in `Then`.
- For exception tests, define an action lambda in `When`, then assert with
  `shouldThrowWithMessage<BusinessException>(ErrorCode.X.message)` in `Then`.

Example:

```kotlin
When("input is invalid") {
    val create = {
        domain.method()
    }

    Then("throws a business exception") {
        shouldThrowWithMessage<BusinessException>(ErrorCode.SOME_CODE.message) {
            create()
        }
    }
}
```

- For exception tests, don't make redundant tests. Only make tests for exceptions explicitly
  mentioned in the target class. For example, `ErrorCode.FOLDER_DIFFERENT_OWNER` is generated only
  in `Folder.kt` domain `validateOwner` method, don't test this on the service calling this method.

## Domain And Exception Rules

- Use `BusinessException` for intentional domain validation failures and service-level business
  failures.
- Every `BusinessException` must be backed by an `ErrorCode`.
- Prefer existing primitive validation extensions such as `requireTrue`, `requireFalse`, and
  `requireNotOver`.
- Keep `ErrorCode` grouped by domain. Each entry must include external response `code`, HTTP
  `status`, and user-facing `message`.
- Use `kotlin.time.Instant` for domain time values.

## Persistence Rules

- Use Exposed `timestamp(...)` for database timestamp columns.
- Convert between Exposed/JVM time and domain `Instant` with `toKotlinInstant()` and
  `toJavaInstant()`.
- Repositories must not open transaction boundaries directly. Services own transaction boundaries
  through `TransactionRunner`.
- Keep domain insert/update mapping in table files using `fromDomain` and `toDomain`-style extension
  functions.
- For read-only SELECT queries, follow the minimum data principle and project into DTO/query models
  instead of loading full domains when unnecessary.
- Keep `ResultRow` to query DTO mapping next to the relevant DTO/query declarations.

## DTO Rules

- Place DTOs under each domain's `dto` package.
- Keep DTO files limited to `Requests`, `Responses`, and `Queries`.
- Declare request, response, and query data classes in the matching file.
- Mark API request/response DTOs with `@Serializable`.

## API Rules

- Use `respondSuccess` for success responses and `respondError` for error responses.
- Manage API resources under each domain's `route` package, using nested classes to represent nested
  paths.

## Documentation

- Write smiley4 OpenAPI documentation in `*Docs.kt` under `route` package of each domain package.
- Include all examples of errors which can happen in the api call, using `SimpleBodyConfig.examples`
  extension in `ErrorExamples.kt`.
- For authentication routes, include `authErrorResponse()` from `ErrorExamples.kt` in the
  `{domain}/route/{Domain}Docs.kt`.

## Constructor Rules

- When calling a constructor with two or more parameters, use named arguments.
- This is especially important for domain objects, DTOs, and fixtures so constructor calls read like
  a lightweight builder.

## Fixture Rules

- Place fixtures under `src/test/kotlin/support/fixture`.
- Separate fixtures that create generally valid objects from fixtures that create objects tied to a
  specific owner or relationship.
- Generate random values through `RandomFixture`; do not scatter direct faker calls across test
  bodies.

## Service Rules

- Model services as application use cases.
- Service public methods should call repositories inside `TransactionRunner.required` or
  `TransactionRunner.readOnly`.
- Services should convert request DTOs into domains and repository results into response DTOs.
- Check use-case preconditions, such as authenticated user existence and related resource existence,
  in the service layer.
