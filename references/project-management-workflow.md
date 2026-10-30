# Project Management Workflow

Requirements and domain design are settled in a GitHub Issue, stage and time are tracked in the shared GitHub Project, code and verification results live in the PR.

## Principles

- A work item is one server capability: a business procedure the server completes on its own, with its rules, permissions and error codes held together. It is never a single endpoint by default, and never the whole user flow.
- The user flow belongs to the parent channel Issue. The backend Issue holds only the business procedure the server performs and the API contract it exposes.
- Discussion happens in chat, confirmed results go in the Issue, code and verification go in the PR.
- The Issue body always holds only the latest confirmed version. Never record change rationale or superseded options; the discussion history stays in the chat session archive.
- Section 1 is written by the user. Sections 2-5 are written by the agent, each when that stage is confirmed.
- Never start the next stage before the user confirms the current one. Write the confirmed result into the Issue, report the stage is closed, and stop.

## Starting an Issue

An Issue starts in one of two ways.

- **From a channel parent Issue.** When the channel Requirement stage finds server work, the agent opens the backend Issue, drafts section 1 from the parent, and links it as a sub-issue of the parent with the GraphQL `addSubIssue` mutation. The API contract lives only in the backend Issue; the parent refers to it by number.
- **On its own.** The user opens the Issue and writes section 1, or starts in chat and the agent drafts section 1 and opens the Issue itself. Infra work usually starts this way.

Either way, the Issue is added to the shared Project with `gh project item-add` and its `Status` set to `Requirement`.

## Project Status

`Status` moves with the Issue body. When a stage is confirmed and written into the body, set `Status` to the next stage the type does not skip; when the PR opens, set it to `PR`. `gh project item-edit` needs the project, item, field and option ids, which come from `gh project list --owner QLink-Bookmarks`, `gh project item-list` and `gh project field-list`. The token needs the `project` scope (`gh auth refresh -s project`).

## Editing the Issue body

`gh issue edit <n> --body` replaces the whole body of that same issue. There is no partial edit, so always read the current body first, swap only the section being confirmed, and write the rest back byte-identical:

```
gh issue view <n> --json body -q .body > /tmp/body.md
# edit only the one section in /tmp/body.md
gh issue edit <n> --body-file /tmp/body.md
```

The user may edit the body directly, so a blind full-body write destroys their changes.

## Issue types

The organization has issue types enabled. `gh` cannot set them, so create the issue with `gh issue create` and then set the type with the GraphQL `updateIssue` mutation (`issueTypeId`); type ids come from `gh api orgs/QLink-Bookmarks/issue-types`. `Infra` is used only in this repository.

Each type has a default set of stages. Skipping is the default, not a rule: the user can ask for a skipped stage in chat.

| Type | 2 Requirement | 3 Domain Review | 4 Test Criteria | 5 PR |
|---|---|---|---|---|
| Feature | business procedure and API contract | yes | yes | yes |
| Bug | reproduction, expected behavior and cause | skip, unless the fix moves a domain responsibility | yes, including the regression | yes |
| Refactor | scope and what must not change | yes when the domain structure changes, otherwise skip | yes, proving existing behavior holds | yes |
| Infra | goal, target environment and blast radius | skip | `terraform plan` under pipeline conditions | yes |

## Stages

The Project is shared with qlink-channel, so its `Status` field holds the stages of both repositories. This repository uses `Requirement`, `Domain Review`, `Test Criteria`, `Implementation`, `PR`, `Complete`, matching the field exactly.

| Status | Issue section | Agent | User |
|---|---|---|---|
| `Requirement` | 2 | Drafts the business procedure and API contract from the request, the parent Issue and the repo, per `references/api-design.md` | Reviews in chat, confirms the final version |
| `Domain Review` | 3 | Proposes where each responsibility lives and what new models are needed, per `references/domain-review.md` | Discusses in chat, confirms the design |
| `Test Criteria` | 4 | Drafts domain and service test cases, per `references/test-criteria.md` | Reviews in chat, confirms the final version |
| `Implementation` | — | Implements and tests against the confirmed criteria | Instructs the agent to start |
| `PR` | 5 | Opens the PR(s), records actual test results, applies feedback | Reviews the PR |
| `Complete` | — | — | Every linked PR is merged or closed |

### 1. Request

The user writes `1. 요청 [USER]` roughly and gives the Issue number in chat, or the agent drafts it as described in Starting an Issue.

### 2. Requirement

Read the parent Issue, if any, and the repo for context before drafting. Do not restate the user flow. Cover:

- The business procedure the server performs, step by step.
- Rules and invariants, permissions and ownership, preconditions such as resource existence, state transitions, and side effects such as notifications or cascades.
- The API contract: method, path, auth, request, success status, response shape, and the `ErrorCode` each failure returns, designed per `references/api-design.md`.
- What is out of scope.

### 3. Domain Review

Design a cohesive domain before any test or code is written: where each rule from section 2 lives, which new domains, VOs, enums and Query DTOs are needed, what the application service handles as the use-case facade, which class throws each `ErrorCode`, and how the work splits into PRs. Apply the decision rules and the fixed section form in `references/domain-review.md`, discuss in chat, and write only the confirmed design.

### 4. Test Criteria

Domain and service test cases that prove the business procedure, each tagged with its target, per `references/test-criteria.md`. Draft them, review them with the user in chat, then write the confirmed list into section 4 as checkboxes. For `Infra`, the criteria are the expected `terraform plan` result instead.

### Implementation

When the user starts this stage, prepare an isolated checkout before changing code:

1. Fetch `origin/main`. Move to the local `main` branch in a safe checkout: switch the current checkout only when it is clean and unused by another session; otherwise check out `main` in a temporary preparation worktree without disturbing the current checkout.
2. Fast-forward `main` to `origin/main` with `git merge --ff-only origin/main` and verify that they point to the same commit. If fast-forward fails, stop and report the divergence; do not merge, rebase, force-reset, or discard existing work.
3. Create an Issue-specific branch and worktree from the updated `main`. Attach the worktree to the task when using a managed worktree. Implement and run tests only in the Issue worktree. Remove the temporary preparation worktree after the Issue worktree is ready.

### 5. PR

Each PR body carries the same checklist from section 4, with only the items actually verified checked off. When the work spans several PRs, every PR but the last links the Issue with `- Refs #n`, and the last one with `- Resolves #n`, so the Issue closes only when all of them land. Section 5 of the Issue records every PR link.

## Skipping sections

- Which stages a type skips by default is in the issue types table above.
- Confirm what to skip in chat. Mark a skipped section by keeping its heading and writing `(생략)`.
- `Requirement` (2) and `Test Criteria` (4) are never skipped.

## Resuming

The Issue body is the single source of truth for what is confirmed. Given only an Issue number, read the body, treat the last filled section as the completed stage, and ask the user to confirm the next one. Never re-open a stage the body already records as confirmed. When the Project `Status` disagrees with the body, the body wins: report the mismatch and set `Status` to match it.
