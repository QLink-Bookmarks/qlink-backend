# API Design

Applies to the API contract in Issue section 2 for every new or changed endpoint. The contract is a
promise to qlink-channel clients: settle it in Requirement, and never let implementation reshape it
silently.

Principle: the URL names a resource, the method says what happens to it, the status and ErrorCode
say how it ended. The acting user comes only from the token.

## Procedure

1. Name the resources the capability touches (U).
2. Pick the method and success status of each operation (M).
3. Define the request, response and list shapes (I, O, L).
4. Decide the auth mode (X) and the access policy, then list every failure as ErrorCode + status (F).
5. If an existing contract changes, classify the change (V).

Complete when every contract row has method, path, auth, request, success status, response and
ErrorCodes, section 2 states the access policy of every resource, and every deviation from a rule
below is written in section 2 with its reason.

## U. URL

- U1. Base `/api`. Path is `/{collection}[/{id}[/{sub}[/{subId}]]]`, at most one sub-collection
  (`/folders/{id}/members/{memberId}`). Deeper → make the sub a top-level collection with a filter.
- U2. Collections are plural domain nouns (`links`, `folders`). Multi-word segments are kebab-case.
  Path variables are `{id}`, and `{<name>Id}` below the first level.
- U3. The caller's own resources live under `/users/me/...`. Never take the caller's userId from
  the path, query or body.
- U4. A state of one resource is a noun sub-resource set with PUT: `/links/{id}/favorite`,
  `/todos/{id}/completed`, `/notifications/{id}/read`. The body is the target state, non-null.
  Never a toggle.
- U5. An operation that is not a state change of one resource is a POST to a noun naming what it
  produces (`/folders/{id}/invitations`).
- U6. No verbs in paths. `/auth/**` is exempt: it models session actions (sign, signout, refresh).
- U7. Path segments are lowercase. Query parameters are camelCase.

## M. Method and status

| Operation | Method | Success | data |
|---|---|---|---|
| Read one | GET `/{id}` | 200 | resource |
| List | GET collection | 200 | `ScrollResponse` |
| Create with server id | POST collection | 201 | `{ "id" }` |
| Create or replace by natural key (device token, user AI key) | PUT | 200 | O3 |
| Replace whole resource | PUT `/{id}` | 200 | O3 |
| Partial update | PATCH `/{id}` | 200 | O3 |
| Set a state (U4) | PUT sub-resource | 200 | O3 |
| Action producing a resource (U5) | POST | 201 | `{ "id" }` or the produced value |
| Accepted for async work | POST | 202 | none, or a tracking id |
| Delete | DELETE | 200 | none |

- M1. GET changes no state and sends no notification.
- M2. PUT and DELETE are idempotent: repeating the request leaves the same state. DELETE of a
  missing resource → 404 with the domain's NOT_FOUND code.
- M3. PUT sends every field; an omitted optional field means "clear it". In PATCH, absent or null
  means "unchanged". A field that must be clearable is not offered in PATCH; use PUT.
- M4. Success always goes through `respondSuccess`. 204 is not used, because the envelope is
  always present; "no data" is 200 with `data: null`.

## I. Request

- I1. JSON body, camelCase. Booleans start with `is`, `allows` or `has`.
- I2. Refer to another resource by `<name>Id: Long`. Nested bodies only for children the created
  aggregate owns (todos of a link).
- I3. An instant is `Instant`, ISO-8601 UTC (`2026-10-28T03:00:00Z`). A wall-clock schedule is
  `HH:mm` plus a separate IANA zone (`Asia/Seoul`).
- I4. Enums are sent by constant name. Constants are readable UPPER_SNAKE words, never single
  letters. Enum values in a query are case-insensitive (`SearchOrder.from`).
- I5. Required means non-null with no default. Optional means nullable with `= null`, and section 2
  states what absence means.
- I6. Unknown fields, missing fields and wrong types are rejected by the framework as
  `COMMON_BAD_REQUEST`. Never set `ignoreUnknownKeys` on API DTOs. Business validation (blank,
  length, URL format) is a domain ErrorCode; the route does not validate.

## O. Response

- O1. Envelope `ApiResponse{success, data, error}`. `data` is an object, never a bare array or
  primitive.
- O2. Create returns `{ "id" }` only.
- O3. A change (PUT, PATCH) returns the updated resource whenever the change produces a value the
  client did not send: generated, normalized, derived or joined (`updatedAt`, trimmed tags,
  `sharedAt`, `folderName` after a `folderId` change). The shape is the resource's GET detail
  response, or a subset of it holding every such value. A change that produces no such value
  returns no data. Never a DTO that only echoes the request or the path. Any other choice is
  reviewed in Requirement and written in section 2 with its reason.
- O4. Nullable fields are always present as `null`. Collections are `[]`, never null.
- O5. Expose only what the caller may see: no secrets (an API key at most masked), no other user's
  private fields.
- O6. One concept has one field name across requests, responses and domains (`folderId`,
  `isFavorite`, `createdAt`, `completedAt`).

## L. List

- L1. A collection GET uses cursor scroll: query `cursor`, `size`; response
  `ScrollResponse{isEmpty, contents, nextCursor, hasNext}`. No offset or page. Only a list the
  domain caps at a fixed size may return `{ "<name>s": [] }` without scroll (folder members).
- L2. `size` defaults to `DEFAULT_SCROLL_SIZE` (15) and has no upper limit.
- L3. `order` is a lowercase `SearchOrder` value the resource supports, default `latest`.
  Unsupported → `COMMON_INVALID_SORT_ORDER`. The cursor is opaque and bound to the order; a
  mismatch → `COMMON_CURSOR_ORDER_MISMATCH`.
- L4. A filter is named after the response field it filters (`folderId`, `isFavorite`,
  `isCompleted`, `type`). Free-text search is `query`. An invalid value → `COMMON_INVALID_FILTER`.
- L5. A count is its own GET sub-resource returning `{ "count" }` (`/notifications/unread`).

## X. Auth

- X1. `authenticate` by default. `authenticate(optional = true)` only for public reads whose
  response differs for a signed-in user (posts, `isMine` on AI models), using
  `jwtPrincipalOrGuest()`. No auth only for sign-in, refresh and public catalogs.
- X2. A cookie-authenticated endpoint that changes state installs the CSRF header check.
- X3. The contract table's 인증 column holds `required`, `optional` or `none`.

## F. Failure

| Status | Meaning | Example |
|---|---|---|
| 400 | A request value breaks an input rule or invariant | `FOLDER_NAME_BLANK` |
| 401 | No or invalid token. Not listed per API; covered by `authErrorResponse()` | `AUTH_*` |
| 403 | Access is restricted and the resource's existence may be disclosed (F2) | `LINK_DIFFERENT_OWNER` |
| 404 | The path resource or a referenced id in the body does not exist, or access is restricted and existence is hidden (F2) | `LINK_FOLDER_NOT_FOUND` |
| 409 | Conflicts with existing state: duplicate, already done | `FOLDER_DUPLICATE_NAME` |
| 422 | Valid and permitted, but the current state forbids it | `LINK_COPY_NOT_SHARED_FOLDER` |
| 502 | An external system failed to communicate or returned an invalid response | `AI_EMPTY_RESPONSE` |
| 503 | An external system is temporarily unavailable, rate-limited or timed out | — |
| 500 | Server fault | `COMMON_INTERNAL_SERVER_ERROR` |

- F1. Every failure in the contract table names its ErrorCode. The code belongs to the domain of
  the capability, even for a referenced resource (`LINK_FOLDER_NOT_FOUND`, not `FOLDER_*`).
- F2. Access to someone else's resource is a requirement decision, never a default. Section 2
  states one policy per resource, and the status follows it:

  | Policy | Caller is not permitted |
  |---|---|
  | Public | — (200) |
  | Restricted, existence may be disclosed | 403 |
  | Restricted, existence is hidden | 404, same code as a missing resource |

- F3. A credential or key the client sent that the external system rejects is the client's input:
  4xx (`AUTH_PROVIDER_TOKEN_INVALID`, `AI_API_KEY_INVALID`), never 502/503.
- F4. A 5xx body never exposes internal class names or exception messages.
- F5. ErrorCode naming and messages follow `references/domain-review.md` E.

## V. Changing an existing contract

- V1. Breaking: removing or renaming a path or field, changing a type, making a request field
  required, making a response field nullable, changing a success status, changing the
  ErrorCode/status of an existing failure, or changing what a value means. Non-breaking: a new
  endpoint, a new optional request field, a new response field.
- V2. A breaking change needs the channel parent Issue to state the client change, and section 2 to
  record the deploy order. Prefer expand then contract: add the new contract alongside the old, let
  clients move, remove the old in a later Issue.
- V3. No URL versioning (`/v2`).
- V4. The deviations below are never fixed inside a feature Issue. Each fix is its own Refactor
  Issue under V2. A new API never copies a deviation.

## Existing deviations

| Rule | Where | Now | Target |
|---|---|---|---|
| U5 | `FolderResources.ById` POST | `POST /folders/{id}` creates an invitation | `POST /folders/{id}/invitations` |
| M | `FolderResources.ById.Members` PUT | Accepting an invitation is PUT, 200 | `POST /folders/{id}/members`, 201 |
| U1, M | `LinkResources.Ai` PUT | `PUT /links/ai`, 200, for a summary request accepted for async work | POST, 202 |
| U4 | `SetLinkFavoriteRequest` | `isFavorite` is nullable, and null is a no-op | non-null |
| I4 | `LinkStatus` | `G` / `A` / `C` / `F` | readable names |
| M | `AiResources.UserProviders` PUT | upsert returns 201 | 200 |
| U3 | `/ai/users/providers` | the caller's resource is outside `/users/me` | `/users/me/ai-providers` |
| O3 | `UpdateLinkResponse`, `UpdateTodoResponse` | echo the request | O3 |
| O3 | `UpdateFolderResponse`, `AcceptFolderInvitationResponse` | echo the path id; `sharedAt` is derived but not returned | O3 |
| O6 | `CompleteTodoResponse` | `completeAt` | `completedAt` |
| F4 | `ApiExceptionHandler`, `respondError` | 5xx bodies expose `cause` / `causeMessage` | removed |
| F | `AUTH_PROVIDER_COMMUNICATION_FAILED`, `AI_VENDOR_TEMPORARY_UNAVAILABLE` | 422 | 502 / 503 |

## Contract table form

| Method | Path | 인증 | 요청 | 성공 | 응답 | ErrorCode |
|---|---|---|---|---|---|---|
| POST | `/api/folders` | required | `name`, `emoji?`, `isShared?` | 201 | `{ id }` | `FOLDER_NAME_BLANK`(400), `FOLDER_NAME_OVER_MAX`(400), `FOLDER_OWNER_NOT_FOUND`(404), `FOLDER_DUPLICATE_NAME`(409) |
