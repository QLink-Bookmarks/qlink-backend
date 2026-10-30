---
name: issue-workflow
description: Run qlink-backend GitHub Issue work through the project management workflow. Use when the user gives a backend Issue number, asks to start, continue or move an Issue through a stage (Requirement, Domain Review, Test Criteria, Implementation, PR), or asks to open a backend Issue from a channel parent Issue or a chat discussion. Examples - "이슈 12 진행", "#12 이어서", "도메인 리뷰 해줘", "이거 이슈로 등록해줘".
---

# Issue Workflow

`references/project-management-workflow.md` holds every rule. Read it first, every time. This skill only finds where the work stands and which reference the stage needs.

## 1. Load the Issue

No Issue number and new work → follow `Starting an Issue` in the workflow, then continue here.

With a number, read type, body and Project status in one call:

```
gh api graphql -F n=<n> -f query='query($n:Int!){repository(owner:"QLink-Bookmarks",name:"qlink-backend"){issue(number:$n){number title state issueType{name} body parent{number repository{name}} projectItems(first:5){nodes{id project{number title} fieldValueByName(name:"Status"){... on ProjectV2ItemFieldSingleSelectValue{name}}}}}}}'
```

When it has a channel parent, read the parent body too.

## 2. Find the stage

1. Skipped stages come from the type, per the workflow's issue types table. No type set → ask the user for it before anything else.
2. The last filled section in the body is the completed stage. `(생략)` counts as filled.
3. The next stage is the first section after it that the type does not skip.
4. Compare with the Project `Status`. The body wins; report a mismatch and fix `Status`.
5. Section 5 lists PRs → check each with `gh pr view <pr> --json state`. All merged or closed → `Complete`.

Tell the user the Issue, its type, the completed stage and the next one, then ask to proceed.

## 3. Run the stage

| Stage | Read | Writes |
|---|---|---|
| `Requirement` | workflow `2. Requirement`, `references/api-design.md`, the parent Issue | section 2 |
| `Domain Review` | `references/domain-review.md` | section 3 |
| `Test Criteria` | `references/test-criteria.md` | section 4 |
| `Implementation` | workflow `Implementation`, `AGENTS.md`, sections 3 and 4 | code and tests in the Issue worktree |
| `PR` | workflow `5. PR` | PR body, section 5 |

## 4. Close the stage

Draft in chat, wait for the user's confirmation, write only that section back with the workflow's body edit procedure, move `Status`, report the stage closed, and stop. Never start the next stage in the same turn.
