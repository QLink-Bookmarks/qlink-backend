# Domain Review

Decision rules for Issue section 3. Read this at the `Domain Review` stage of `references/project-management-workflow.md`.

These rules apply to every change in `domain/**` and `service/**`, not only at that stage.

Every rule is written so that, applied in order, it yields exactly one answer. When it does not, follow Ambiguity below instead of choosing.

The principle behind them: tell, don't ask, and keep each boundary cohesive with its context. An object that holds the state makes the decision about it.

## Procedure

1. Read every domain, service and repository the requirement touches, and the `common` packages listed under Common.
2. List every rule from section 2 (invariant, permission, precondition, state transition, side effect) and every `ErrorCode`.
3. Place each rule with Placement, design new types with Model and File, check every dependency with Dependency, decide shared helpers with Common, shape use cases with Service, design read paths with Query, and assign each `ErrorCode` with Error.
4. Draft section 3 in the fixed form below, citing the rule id on every line, review it with the user in chat, then write the confirmed version.

Section 3 is complete only when every section 2 rule appears in exactly one `규칙 배치` line, every `ErrorCode` has a row in the `ErrorCode` table, and every added or changed `common` function lists its callers under `공통`.

## Placement

Apply in order. The first rule that matches decides.

- **P1** Decided from one domain object's own state and the method's arguments → a method on that domain. Invariants checked at creation or update go in `init` or the factory.
- **P2** Decided from objects of two or more domains already loaded, with no repository → a domain service in the `domain/service` package of the domain the use case changes; for a read, the domain being accessed. A domain service is an `object` with no repository and no I/O. Each domain contributes its facts through its own P1 methods, which take primitives or that domain's own types. The domain service only reads those facts, combines them and builds the combined domains' instances through their factories. An application service calls a domain service only for such a decision; a decision one domain makes alone goes to that domain directly (P1).
- **P3** Decided by a repository result alone (existence of the authenticated user or a related resource, uniqueness, count) → the application service, with `requireTrue`, `requireFalse` or `?: throw`. When the result has to be interpreted (role, state, time), load it as a domain and go back to P1 or P2.
- **P4** Ordering, cascades across domains, transaction boundaries, external clients (AI, push, storage) and notification dispatch → the application service.
- **P5** Never in a route, repository or table mapping.

A service never reads a domain's fields to make a decision P1 or P2 assigns to the domain. `if (folder.ownerId == loginId)` or `folder.sharedAt != null && …` in a service is a violation; the domain or domain service exposes the decision as a method.

## Model

- **M1** A new domain exists only when it has all three: its own identity, its own lifecycle (created and deleted on its own), and its own table. Missing any one, it is not a domain.
- **M2** A value without identity:
  - A closed set of values → an enum in the owning domain's package, parsed by a `from`-style function.
  - A VO only when the same validation or behavior is needed by two or more domains, or two or more fields are valid only together. This decides how a domain's own fields are modeled. A type that F2 step 2 or step 4 classifies as a VO is a VO regardless.
  - Otherwise a primitive, validated by a private function inside the domain.
- **M3** A domain refers to another domain by id. It never holds another domain object as a field, never takes one as an argument and never imports another domain's package. Among domain-layer code, only a domain service (P2) imports more than one domain. A domain or VO never uses `dto` types, its own domain's included. Domain services and application services assemble DTOs from domain results.
- **M4** A domain is immutable; a state change returns a new instance. Domain time values are `kotlin.time.Instant`. A domain never reads the clock, not even as a default argument; the service passes the time in.
- **M5** Each domain has its own package. A domain with no use case of its own has only `domain` and `repository` packages.
- **M6** A domain service is an `object` named `<Noun>DomainService`, such as `ImportPlanDomainService` or `ImportTrackDomainService`, in `<domain>/domain/service`. A class only a domain service uses lives next to it as an `internal` class in its own file and is never named `DomainService`. Application services keep S1 naming, `<Verb><Noun>Service`, in `<domain>/service`.

## File

- **F1** One top-level type per file, and the file is named after it. Nested types (companion, sealed subtypes declared inside the parent) belong to their outer type. The only exception is a DTO file in `<domain>/dto`, `<Domain>Requests.kt`, `<Domain>Responses.kt`, `<Domain>Queries.kt` or `<Domain>Results.kt`, which holds every DTO of that kind for the domain.
- **F2** Classify each type in `domain`, `domain/service` and `dto` in order. The first match decides. Infrastructure types (clients, workers and signals, repository query expressions, configuration) are outside F2; F1 still applies to them.
  1. It has its own table row → a domain, or a sub-domain row stored by the parent's repository (M1), in `domain`.
  2. It has a method, a computed property, an `init` check or a factory that enforces a rule → a domain or VO in `domain`, in its own file, even when M2's VO conditions do not hold; or, when only a domain service uses it, an `internal` helper next to it (M6).
  3. It is a closed set of values → an enum in `domain`, in its own file (M2).
  4. A value held or returned by a domain or VO, or taken as its argument, is a VO in `domain` even without behavior (domains never use DTOs, M3).
  5. Otherwise it only carries fields between steps or layers → a DTO in `dto`: `Requests` and `Responses` for the API (serialized), `Queries` for repository projections and cursors, `Results` for values returned by domain services (never by domains, M3).
- **F3** A helper is pure mechanism when it uses no domain constant and no `ErrorCode`. A pure-mechanism extension or helper on a Kotlin or JDK type (`String`, `Long`, `Instant`, `URI`, `ByteArray`, collections of them) is never a private function in a `domain`, `domain/service`, `service` or `repository` file. It goes in a `common` util file of its kind (C1, C3). The util holds the mechanism; the domain keeps the policy and passes it in (C2), as `url.toNormalizedUrlOrNull(ignoredQueryKeys = TRACKING_QUERY_KEYS, ignoredQueryKeyPrefixes = TRACKING_QUERY_KEY_PREFIXES)` does. A private function that applies the domain's own constants or `ErrorCode`, such as M2 validators or `comparableUrlOf`, stays in the domain. Extensions on the project's own types stay private in the file that uses them. Extensions on library types (Exposed, Ktor) stay where they are used. A class that is not an extension and serves one domain stays in that domain's package, in its own file (F1).

## Dependency

A type depends on another when it names, holds, takes, returns or calls it. Only the dependencies listed below are allowed, one line each. Anything not listed is not allowed, and no dependencies form a cycle. A domain service is optional. It exists only for a decision that spans several domains (P2), so a call never has to go through one. Every item may use `common` (C2). M3 still limits which domains a domain-layer type may name.

- **D1** Route → application service, always, and the request and response DTOs it maps. A route calls nothing else.
- **D2** Application service → repository; → domain (its behavior); → domain service, only when a decision spans domains (P2); → DTO; → client (P4); → worker, only when a case needs it, such as a wake signal. It may also use its own helper classes (S4) and a shared-step service (S5). It may build, pass and read VOs and enums, but calls their behavior only when no other way exists.
- **D3** Worker → usually nothing; it runs alone. When it has a structure, the worker is only the trigger: worker → application service, which then goes → repository and domain (D2).
- **D4** Repository → its tables (persistence) and its own domain's domains, VOs and enums; → DTOs, always, such as `Queries`.
- **D5** Domain service → domains, VOs, enums and its own `internal` helpers (M6). It builds DTOs (M3).
- **D6** Domain → its own VOs and enums. An aggregate root → its own child rows (sub-domain rows stored by the root's repository, M1), as `BookmarkImport` → `ImportFolder`, `ImportLink`, `ImportFailure`.
- **D7** VO → the VOs it holds and enums. A constant that limits a VO's own field lives in that VO; a parent reads it from the child, never the other way. Enum → nothing.
- **D8** DTO → may hold domains, VOs and enums to carry them between layers; it never calls their methods.
- **D9** Config (injected singletons) → any layer may use it where needed.

## Common

`common` holds mechanisms shared across domains. It never holds a business rule.

Review targets, by kind:

| Package | Holds |
|---|---|
| `common/error` | `PrimitiveExtensions.kt` validation extensions that take an `ErrorCode`; `ErrorCode`, `BusinessException` |
| `common/text` | string transforms that never fail, including URL normalization |
| `common/collection`, `common/number` | collection helpers and numeric conversions |
| `common/search`, `common/scroll` | query expressions, cursor codecs, scroll request and response |
| `common/retry`, `common/crypto` | retry and encryption mechanisms |
| `common/transaction`, `common/response`, `common/docs`, `common/cookie` | transaction runner, API response and error handling, OpenAPI examples, auth cookies |

- **C1** Apply in order whenever a rule needs a helper or extension:
  1. An existing `common` function already does it, such as `requireTrue`, `requireFalse` or `requireNotOver` → reuse it. Never add a near-duplicate.
  2. An existing `common` function almost does it → change it only when every current caller keeps its behavior (C4). Otherwise add a new function.
  3. Only one domain needs it, and it works on that domain's own types or applies that domain's constants or `ErrorCode` → a private function or extension inside the file that uses it. When it is pure mechanism on a Kotlin or JDK type, go to step 4 (F3).
  4. Two or more domains need it, or it is pure mechanism on a Kotlin or JDK type (F3), and it knows nothing about any domain → add it to `common`, in the package of its kind.
- **C2** Mechanism goes in `common`; policy stays in the domain. A `common` function never names a domain type, never picks an `ErrorCode`, and never fixes a business value. The domain passes its limits and its `ErrorCode` in, as `name.requireNotOver(MAX_FOLDER_NAME_LENGTH, ErrorCode.FOLDER_NAME_OVER_MAX)` does. When two or more domains share the same concept with the same constants, it is a VO (M2), not a `common` function.
- **C3** A new validation extension goes in `common/error/PrimitiveExtensions.kt` as `require<Condition>(…, errorCode: ErrorCode)`, with the `ErrorCode` last. Any other new helper goes in the existing package of its kind. A new `common` package is added only when no existing kind fits, and is named for the kind, never for a domain.
- **C4** Changing an existing `common` function lists every caller in section 3. When any caller would behave differently, add a new function instead of changing it.
- **C5** A change to `common/transaction`, `common/response` or `ApiExceptionHandler` affects every API, so it is never bundled into a `Feature` or `Bug`. It becomes its own `Refactor` Issue.
- **C6** `common` and DTOs (`<domain>/dto`) are outside the Jacoco scope (`**/domain/**`, `**/service/**`) and are never tested directly. Their behavior is proven through the domain and service cases that use them, so Test Criteria lists those cases on the using domain.

## Service

The application service is the use-case facade.

- **S1** One use case is one class, `<Verb><Noun>Service`, with one public method.
- **S2** A use case lives in the package of the domain it changes; a read lives in the package of the resource its API path addresses. When it changes several domains, the one its API path addresses.
- **S3** The public method follows the section 2 procedure in this order: preconditions (P3) → load domains → domain decisions (P1, P2) → persist → side effects (P4) → response. The service converts request DTOs into domains and repository results into response DTOs.
- **S4** A repository-dependent check used by two or more services becomes a helper class in the service package. Used by one service, it stays private. A helper holds only P3 and P4 logic.
- **S5** A service calls another service only when that service is a shared step with no route of its own, such as notification scheduling. It never calls a use case that a route calls.
- **S6** The service owns the transaction boundary: every public method calls repositories inside `TransactionRunner.required` or `TransactionRunner.readOnly`. A repository never opens a transaction.

## Query

- **Q1** A read path that involves any domain decision (ownership, permission, state rule) loads the deciding domains, decides with them, then makes a separate query into a Query DTO for the response.
- **Q2** A read path with no domain decision follows the minimum data principle: it projects straight into a Query DTO holding only the columns the response needs, never loading full domains.
- **Q3** A Query DTO never carries a field used only for a decision. Such a field means Q1 applies.

## Error

- **E0** An intentional domain validation failure or service-level business failure throws `BusinessException`, always backed by an `ErrorCode`.
- **E1** An `ErrorCode` thrown by a domain (P1, P2) uses that domain's prefix.
- **E2** An `ErrorCode` thrown by a service or helper (P3) uses the prefix of the use case in which the failure is reported, one code per context, e.g. `LINK_FOLDER_NOT_FOUND` and `LINK_TARGET_FOLDER_NOT_FOUND`.
- **E3** Reuse an existing `ErrorCode` only when both the thrower and the meaning are the same. Otherwise add a new one, numbered after the last code of that prefix in `ErrorCode.kt`.
- **E4** `ErrorCode.kt` stays grouped by domain. Each entry has the external response `code`, the HTTP `status` and the user-facing `message`.

## Existing code

Existing code that breaks these rules is not precedent. Never copy it. When the Issue touches it, list it under `기존 위반` with the rule it breaks, and confirm in chat whether this Issue fixes it or leaves it.

Known now:

- `FolderAccessValidator` decides ownership and member write permission in the service (P2).
- `GetLinkDetailService` checks access on `LinkDetailQuery`, which carries decision-only fields (Q1, Q3).
- `CreateFolderInvitationService` reads `folder.sharedAt` to decide whether the folder is shared (P1).
- `Link.changeFavorite` reads the clock as a default argument (M4).
- `Notification` imports `todo.domain.Todo`, and `User` and `UserProvider` import `auth.domain.Role` (M3).
- `ai/domain/DailyUsage.isOverLimit(model: AvailableModel)` takes another domain as an argument (M3, D6).
- `image/domain/ImagePurpose.newObjectKey(environment, type: ImageType)` makes an enum depend on another enum (D7). `ImageType` is closer to a value type than a domain; recorded as is.
- Files with more than one top-level type (F1):
  - domain: `user/domain/User.kt` (`UserTheme`, `UserAccent`)
  - service: `ai/service/UpdateLinkAiSummaryService.kt`, `notification/service/SendNotificationService.kt`, `auth/service/AuthTokenService.kt`, `auth/service/RandomUserNameGenerator.kt`, `auth/service/RefreshAuthTokenService.kt` (top-level `RefreshResult`)
  - client: `ai/client/AiClient.kt`, `ai/client/GeminiAiClient.kt`, `ai/client/OpenAiClient.kt`, `auth/client/AuthResourceClient.kt`, `auth/client/GoogleAuthResourceClient.kt`, `auth/client/KakaoAuthResourceClient.kt`, `auth/client/NaverAuthResourceClient.kt`, `auth/client/OidcIdTokenVerifier.kt`, `push/client/ExpoPushClient.kt`, `push/client/FirebaseInitializer.kt`, `push/client/PushNotificationSender.kt`, `link/client/LinkPreviewSyncClient.kt`, `link/client/LambdaLinkPreviewSyncClient.kt`
  - config and common: `config/HttpPluginConfig.kt`, `config/MonitoringConfig.kt`, `common/search/SearchCursor.kt`, `common/response/ApiResponse.kt`
- Pure-mechanism private extensions on Kotlin or JDK types (F3): `image/domain/ImageType.kt` `ByteArray.startsWith` and `ByteArray.isWebp`, `auth/service/RandomUserNameGenerator.kt` `List<T>.random`.

## Ambiguity

When the rules yield no answer, or two rules yield different answers, do not choose. Present each option in chat with the rule it satisfies, and let the user decide. When the decision would recur, propose it as a new rule for this file; once the user confirms it, the rule is added in that Issue's PR.

## Section 3 form

Keep the headings in this order every time. Write `(없음)` under a heading with nothing to record.

Each `공통` entry names the function, its file, its callers and the rule:

- `재사용 — \`String.requireNotOver\` (common/error/PrimitiveExtensions.kt) — Folder (C1)`
- `추가 — \`String.requireWebUrl\` (common/error/PrimitiveExtensions.kt) — Link, Post (C1, C3)`
- `변경 — \`retryWithExponentialBackoff\` (common/retry/Retry.kt) — 호출부: ExpoPushClient (C4)`

Example, for a folder invitation use case:

```
### 규칙 배치
- 로그인 사용자가 존재해야 한다 → `CreateFolderInvitationService` (P3)
- 폴더가 존재해야 한다 → `CreateFolderInvitationService` (P3)
- 폴더 소유자만 초대할 수 있다 → `Folder.validateOwner` (P1)
- 공유 폴더만 초대할 수 있다 → `Folder.validateShared` (P1)
- 초대 토큰은 지정한 기간 뒤 만료된다 → `CreateFolderInvitationService` (P4)

### 도메인 변경
- `Folder` — `validateShared()` 추가

### 신규 도메인·VO·Enum
- (없음)

### 공통
- 재사용 — (없음)
- 추가 — (없음)
- 변경 — (없음)

### Query DTO
- (없음)

### 애플리케이션 서비스
- `CreateFolderInvitationService.createInvitation` (folder, S2)
  1. 사전조건 — 사용자 존재, 폴더 존재 (P3)
  2. 로드 — `Folder`
  3. 판단 — `Folder.validateOwner`, `Folder.validateShared` (P1)
  4. 저장 — (없음)
  5. 부수효과 — 초대 토큰 서명 (P4)

### ErrorCode
| ErrorCode | 던지는 곳 | 규칙 |
|---|---|---|
| `FOLDER_OWNER_NOT_FOUND` | `CreateFolderInvitationService` | E2 |
| `FOLDER_NOT_FOUND` | `CreateFolderInvitationService` | E2 |
| `FOLDER_DIFFERENT_OWNER` | `Folder.validateOwner` | E1 |
| `FOLDER_NOT_SHARED` | `Folder.validateShared` | E1 |

### 기존 위반
- `CreateFolderInvitationService` 가 `folder.sharedAt` 을 직접 읽어 공유 여부를 판단 (P1)

### PR 분할
- 단일 PR
```
