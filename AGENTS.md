# AGENTS.md

Guidance for agents working in this repository. Keep this file focused on project-specific
conventions that are not obvious from reading the code.

## Baseline

- Prefer Kotlin style logics.
- Readability, Simpleness, and Maintenance are the priorities.
- For any change in `domain/**` or `service/**`, follow `references/domain-review.md`.

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
- For domain method tests that are not testing object creation, create the domain object through a fixture. The fixture should expose parameters for values relevant to the method under test, while randomizing unrelated valid values.
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

## Persistence Rules

- Use Exposed `timestamp(...)` for database timestamp columns.
- Convert between Exposed/JVM time and domain `Instant` with `toKotlinInstant()` and
  `toJavaInstant()`.
- Keep domain insert/update mapping in table files using `fromDomain` and `toDomain`-style extension
  functions.
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
- Use field from resources if path variable or query string is needed.

Example:

```kotlin
get<LinkResources.ById> { resource ->
  val principal = call.principal<JwtPrincipal>()!!
  service.getLinkDetail(principal.userId, resource.id)
}
```

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

## References

Consult these resources as needed:

```
references/
  domain-review.md                Domain, VO, common, service, query and ErrorCode placement rules
  project-management-workflow.md  Issue/Project/PR stages, confirmation gates, section rules
  test-criteria.md                Domain and service test case form, coverage axes, Infra plan checks
```
