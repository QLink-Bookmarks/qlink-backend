# Domain Review

Decision rules for Issue section 3. Read this at the `Domain Review` stage of `references/project-management-workflow.md`.

These rules apply to every change in `domain/**` and `service/**`, not only at that stage.

Every rule is written so that, applied in order, it yields exactly one answer. When it does not, follow Ambiguity below instead of choosing.

The principle behind them: tell, don't ask, and keep each boundary cohesive with its context. An object that holds the state makes the decision about it.

## Procedure

1. Read every domain, service and repository the requirement touches, and the `common` packages listed under Common.
2. List every rule from section 2 (invariant, permission, precondition, state transition, side effect) and every `ErrorCode`.
3. Place each rule with Placement, design new types with Model, decide shared helpers with Common, shape use cases with Service, design read paths with Query, and assign each `ErrorCode` with Error.
4. Draft section 3 in the fixed form below, citing the rule id on every line, review it with the user in chat, then write the confirmed version.

Section 3 is complete only when every section 2 rule appears in exactly one `규칙 배치` line, every `ErrorCode` has a row in the `ErrorCode` table, and every added or changed `common` function lists its callers under `공통`.

## Placement

Apply in order. The first rule that matches decides.

- **P1** Decided from one domain object's own state and the method's arguments → a method on that domain. Invariants checked at creation or update go in `init` or the factory.
- **P2** Decided from two or more domain objects already loaded, with no repository → a method on the domain the use case changes; for a read, the domain being accessed. The other domains are passed in as arguments.
- **P3** Decided by a repository result alone (existence of the authenticated user or a related resource, uniqueness, count) → the application service, with `requireTrue`, `requireFalse` or `?: throw`. When the result has to be interpreted (role, state, time), load it as a domain and go back to P1 or P2.
- **P4** Ordering, cascades across domains, transaction boundaries, external clients (AI, push, storage) and notification dispatch → the application service.
- **P5** Never in a route, repository or table mapping.

A service never reads a domain's fields to make a decision P1 or P2 assigns to the domain. `if (folder.ownerId == loginId)` or `folder.sharedAt != null && …` in a service is a violation; the domain exposes the decision as a method.

## Model

- **M1** A new domain exists only when it has all three: its own identity, its own lifecycle (created and deleted on its own), and its own table. Missing any one, it is not a domain.
- **M2** A value without identity:
  - A closed set of values → an enum in the owning domain's package, parsed by a `from`-style function.
  - A VO only when the same validation or behavior is needed by two or more domains, or two or more fields are valid only together.
  - Otherwise a primitive, validated by a private function inside the domain.
- **M3** A domain refers to another domain by id, never by holding the object as a field. A domain method may take another domain object as an argument (P2).
- **M4** A domain is immutable; a state change returns a new instance. Domain time values are `kotlin.time.Instant`. A domain never reads the clock, not even as a default argument; the service passes the time in.
- **M5** Each domain has its own package. A domain with no use case of its own has only `domain` and `repository` packages.

## Common

`common` holds mechanisms shared across domains. It never holds a business rule.

Review targets, by kind:

| Package | Holds |
|---|---|
| `common/error` | `PrimitiveExtensions.kt` validation extensions that take an `ErrorCode`; `ErrorCode`, `BusinessException` |
| `common/text` | string transforms that never fail |
| `common/search`, `common/scroll` | query expressions, cursor codecs, scroll request and response |
| `common/retry`, `common/crypto` | retry and encryption mechanisms |
| `common/transaction`, `common/response`, `common/docs`, `common/cookie` | transaction runner, API response and error handling, OpenAPI examples, auth cookies |

- **C1** Apply in order whenever a rule needs a helper or extension:
  1. An existing `common` function already does it, such as `requireTrue`, `requireFalse` or `requireNotOver` → reuse it. Never add a near-duplicate.
  2. An existing `common` function almost does it → change it only when every current caller keeps its behavior (C4). Otherwise add a new function.
  3. Only one domain needs it → a private function or extension inside that domain's file.
  4. Two or more domains need it, and it knows nothing about any of them → add it to `common`, in the package of its kind.
- **C2** Mechanism goes in `common`; policy stays in the domain. A `common` function never names a domain type, never picks an `ErrorCode`, and never fixes a business value. The domain passes its limits and its `ErrorCode` in, as `name.requireNotOver(MAX_FOLDER_NAME_LENGTH, ErrorCode.FOLDER_NAME_OVER_MAX)` does. When two or more domains share the same concept with the same constants, it is a VO (M2), not a `common` function.
- **C3** A new validation extension goes in `common/error/PrimitiveExtensions.kt` as `require<Condition>(…, errorCode: ErrorCode)`, with the `ErrorCode` last. Any other new helper goes in the existing package of its kind. A new `common` package is added only when no existing kind fits, and is named for the kind, never for a domain.
- **C4** Changing an existing `common` function lists every caller in section 3. When any caller would behave differently, add a new function instead of changing it.
- **C5** A change to `common/transaction`, `common/response` or `ApiExceptionHandler` affects every API, so it is never bundled into a `Feature` or `Bug`. It becomes its own `Refactor` Issue.
- **C6** `common` is outside the Jacoco scope and is never tested directly. Its behavior is proven through the domain and service cases that use it, so Test Criteria lists those cases on the using domain.

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
