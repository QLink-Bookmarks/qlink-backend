# Test Criteria

Domain and service test cases that prove the business procedure confirmed in sections 2 and 3. Read this at the `Test Criteria` stage of `references/project-management-workflow.md`.

Draft the list, review it with the user in chat, then write the confirmed version into Issue section 4.

## Scope

- Only the business is tested: `domain/**` and `service/**`, the same scope as Jacoco.
- The route layer, serialization, auth filters and backward compatibility are trusted and never listed.
- Existing data migration is not listed.

## Form

Each case maps one-to-one to a Kotest `BehaviorSpec` scenario, so section 4 reads as the skeleton of the test files.

- Group cases under one heading per test target, which is one spec file: `### Domain — <Class>` or `### Service — <Service>`.
- Targets come from section 3: each case is tested where its rule is placed in `규칙 배치`, and each `ErrorCode` where its row in the `ErrorCode` table says it is thrown.
- Under the heading, a `Given` line is the shared context. Each checkbox beneath it is one `When` + `Then` pair, tagged with the method under test.
- Line form: `- [ ] \`<method>\` When <action> → Then <result>`
- Write only the condition being tested. Values that are not the subject stay unnamed, because fixtures randomize them.
- A domain `Then` is the returned value, the changed state, or the thrown error.
- A service `Then` is what a repository query shows afterwards, never a return value alone.
- A failure case names its `ErrorCode`: `→ Then ErrorCode.FOLDER_NAME_BLANK`. Only `BusinessException` failures are listed.
- List a failure case only under the class that throws it. A service that calls `Folder.validateOwner` does not repeat `FOLDER_DIFFERENT_OWNER`.
- Pair a success case with its failure case wherever an action can fail.
- For `Bug`, append `(회귀)` to the case that reproduces the reported defect. For `Refactor`, list the existing behavior the refactor must keep.

Example:

```
### Domain — Folder
- Given 폴더를 생성할 때
  - [ ] `create` When 이름과 이모지가 유효하면 → Then 그대로 담긴 폴더가 만들어진다
  - [ ] `create` When 이름이 공백이면 → Then ErrorCode.FOLDER_NAME_BLANK
  - [ ] `create` When 이름이 최대 길이를 넘으면 → Then ErrorCode.FOLDER_NAME_OVER_MAX
  - [ ] `create` When 이모지를 비우면 → Then 기본 이모지 중 하나가 지정된다
- Given 다른 사용자가 소유한 폴더
  - [ ] `validateOwner` When 소유자를 검증하면 → Then ErrorCode.FOLDER_DIFFERENT_OWNER

### Service — CreateFolderService
- Given 로그인한 사용자
  - [ ] `createFolder` When 폴더를 생성하면 → Then 사용자 소유의 폴더가 저장된다
  - [ ] `createFolder` When 공유 폴더로 생성하면 → Then 사용자가 소유자 멤버로 저장된다
  - [ ] `createFolder` When 이미 가진 폴더와 같은 이름이면 → Then ErrorCode.FOLDER_DUPLICATE_NAME
- Given 존재하지 않는 사용자
  - [ ] `createFolder` When 폴더를 생성하면 → Then ErrorCode.FOLDER_OWNER_NOT_FOUND
```

Bad: `- [ ] 폴더 생성이 정상 동작한다` — no target, no checkable result
Bad: `- [ ] \`createFolder\` When 이름이 "abc"이면 → Then 201` — a fixed value that is not the subject, and an HTTP status from the trusted route layer

## Coverage

Eight axes. Every one of them is accounted for in the coverage checklist below, whether or not it produces a test case.

1. **Happy path** — every step of the business procedure in section 2 completes
2. **Input and invariants** — blank, over max length, invalid format, duplicate, limits such as `requireNotOver`
3. **Permission and ownership** — owner, member, outsider, each role the procedure distinguishes
4. **Preconditions** — the authenticated user and every related resource exist
5. **State transitions** — allowed and rejected transitions, and the state after each
6. **Boundaries** — 0 / 1 / many items, pagination edges, the exact limit and one past it
7. **Repetition and idempotency** — the same request twice, re-registration, takeover of an existing record
8. **Side effects** — cascades, notifications, counts, and related data reflecting the change

## Coverage checklist

Section 4 always ends with this fixed block, below the test cases. It is not a list of test cases; it is the record that no axis was dropped unnoticed. Keep the lines in this order and this wording, every time, for `Feature`, `Bug` and `Refactor`. Check an axis once its cases are written, and append `(Not Scope)` to one the issue does not touch.

```
### 커버리지 점검
- [ ] 정상 경로
- [ ] 입력·불변식
- [ ] 권한·소유권
- [ ] 사전조건
- [ ] 상태 전이
- [ ] 경계값
- [ ] 반복·멱등
- [ ] 부수효과·연관 데이터 반영
```

## Infra

`Infra` has no unit tests. Its criteria are the result of `terraform plan` run under the same conditions as `.github/workflows/infra-pipeline.yml`:

- `init`, `fmt`, `validate`, then `plan` in `terraform/<env>`.
- The same set of `TF_VAR_*` names as the pipeline, with plan-only dummy values for secrets.
- The same `-target` list as the pipeline.

Section 4 lists the expected outcome as checkboxes, then ends with this fixed block:

```
### Plan 점검
- [ ] 파이프라인과 동일한 조건으로 plan 수행
- [ ] 예상한 add / change / destroy와 일치
- [ ] 범위 밖 리소스 변경 없음 (더미 secret 값에 의한 차이 제외)
- [ ] 신규 리소스가 파이프라인 -target 목록에 포함
```
