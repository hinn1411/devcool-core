---
name: implement-task
description: Implement one roadmap task by id (e.g. P3-T04) from docs/plans/phases, following its phase plan, the linked ADRs and the hexagonal conventions, with tests.
argument-hint: <task-id, e.g. P3-T04>
arguments: [task]
disable-model-invocation: true
---

# Implement roadmap task $task

Current branch and working tree:
!`git branch --show-current`
!`git status --short`

## 1. Understand
1. Find the line `**$task**` in `docs/plans/phases/phase-*.md`. If it doesn't exist, stop and say so.
2. If `docs/journal/briefs/$task.md` exists, read it first. Its Requirements and Decisions were agreed with the user in `/frame-task`, so build to them and don't ask about them again. If it doesn't exist, suggest `/frame-task $task` in one line and continue.
3. Read that phase file's **Design notes**, **Test plan** and **Definition of Done**, plus every ADR or architecture section it links for this task. Treat the ADRs as decided. If the task as written conflicts with an ADR or with the current code, stop and explain the conflict instead of picking one silently.
4. Check whether earlier tasks in the same phase are unticked. If this task depends on them, say so before continuing.
5. Look at the existing code this touches. Reuse the patterns listed in `.claude/rules/hexagonal.md` rather than inventing new ones. For a broad search, use an Explore subagent so the file dumps stay out of this context.

## 2. Plan
- If you're on `master`, create a branch: `feat/<task-id-lowercase>-<short-slug>` (or `fix/…`, `chore/…`).
- If the change touches more than ~5 files, a DB migration, a public API/protocol, or infra, present a short plan (files, ports, migration, tests) and wait for confirmation. Otherwise proceed.

## 3. Build
- Work through the hexagonal slice in this order: domain model/ports → service → adapters → controller/WS handler → DTO/mapper.
- For migrations, follow `.claude/rules/flyway.md` (use the `flyway-migration` skill for the file name).
- Write the tests from the phase's test plan alongside the code:
  - Mockito unit tests for services.
  - Testcontainers ITs where the plan asks for them.
  - Negative authorization tests for every access rule.

## 4. Verify
- Run the `verify` skill's checks for the changed areas and fix the failures. Don't disable, skip or weaken tests to make them pass.

## 5. Close out
- Tick `- [x] **$task**` in the phase file. If every task in the phase is now ticked, set that phase's status to `done` in `docs/plans/README.md`. If it was `todo`, set it to `in-progress` when you start.
- If you made a decision the ADRs don't cover, say so and suggest `/write-adr`.
- If the implementation departed from the brief, add a row to the brief's **Decisions** table with the reason.
- Summarize: what changed (by layer), the tests added, anything deferred. Don't commit or push unless the user asks.
