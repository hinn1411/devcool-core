---
name: implement-task
description: Implement one roadmap task by id (e.g. P3-T04) from docs/plans/phases, test-first (Red → Green → Review → Refactor), following its brief, phase plan, linked ADRs and the hexagonal conventions.
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
2. If `docs/journal/briefs/$task.md` exists, read it first. Its **Done when** and **Decisions** were agreed with the user in `/frame-task`, so build to them and don't ask about them again. If it doesn't exist, suggest `/frame-task $task` in one line and continue.
3. Read that phase file's **Design notes**, **Test plan** and **Definition of Done**, plus every ADR or architecture section it links for this task. Treat the ADRs as decided. If the task as written conflicts with an ADR or with the current code, stop and explain the conflict instead of picking one silently.
4. Check whether earlier tasks in the same phase are unticked. If this task depends on them, say so before continuing.
5. Look at the existing code this touches. Reuse the patterns listed in `.claude/rules/hexagonal.md` rather than inventing new ones. For a broad search, use an Explore subagent so the file dumps stay out of this context.

## 2. Branch and plan
- If you're on `master`, create a branch: `feat/<task-id-lowercase>-<short-slug>` (or `fix/…`, `chore/…`).
- If the change touches more than ~5 files, a DB migration, a public API/protocol, or infra, present a short plan (files, ports, migration, the Done-when → test map) and wait for confirmation. Otherwise proceed.

## 3. Red: failing tests first
- Map every brief **Done when** item (and the phase **Test plan**) to a named test, and show the map as a short list. Use Mockito unit tests for services (`.claude/rules/testing.md`), Testcontainers ITs where the plan asks, and a negative authorization test for every access rule.
- Add only the production signatures the tests need to compile: port interfaces, records, and methods that `throw new UnsupportedOperationException()`. No logic.
- Run the new tests with the affected-tests commands in `CLAUDE.md` › Commands (unit, or the IT form for ITs). Each must **fail on an assertion or the stub's exception**, not on a compile error or a broken fixture. Show the failing test names.
- Commit the brief and the Red tests: `test($task): <what the tests pin down>`. Don't push: a Red commit fails CI.

## 4. Green: simplest passing code
- Write the simplest code that makes every Red test pass. Work through the hexagonal slice in this order: domain → service → adapters → controller/WS handler → DTO/mapper. For migrations, follow `.claude/rules/flyway.md` (use the `flyway-migration` skill for the file name).
- **Don't edit the Red tests.** If one turns out to be wrong, stop, say why, and get the user's OK before changing it.
- Run the affected tests, unit and IT (`CLAUDE.md` › Commands), not the full suite. Once all are green, commit: `feat($task): <behaviour>`.

## 5. Review: the user decides
- Launch these reviewers **in parallel**, based on `git diff master...HEAD`:
  - `hexagonal-reviewer` for `src/**`
  - `security-reviewer` for auth, WebSocket, AI, infra or IAM
  - `aws-architect` for `infra/**`
- Merge their findings into one list: deduplicate it, rank it by severity, and give each item one line (`path:line` · problem · recommended fix). Add your own improvement ideas, marked as yours.
- Ask one AskUserQuestion: "Apply all recommended" (first), "Let me pick" (then a multiSelect over the items), or "None". Apply nothing that the user rejected.

## 6. Refactor and close
- Apply the approved items and behaviour-preserving cleanups. Re-run the affected tests after each change, and keep them green. If an approved item changes behaviour, write its failing test first.
- Run `./mvnw -q spotless:apply`, then the full unit + IT suite once: `./mvnw -q -Dit verify` (needs Docker; the JaCoCo floor applies). Don't disable, skip or weaken tests to make them pass.
- Then SpotBugs: `./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify`. Fix new findings in code. Never add them to `spotbugs-exclude.xml` (remove-only baseline). A false positive on a constructor-injected Spring bean gets the class-level `@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = …)` used in `ChannelAuthorizer`. No `clean`; the rest of the build runs in CI.
- Tick `- [x] **$task**` in the phase file. If every task in the phase is now ticked, set that phase's status to `done` in `docs/plans/README.md`. If it was `todo`, set it to `in-progress` when you start.
- If the implementation departed from the brief, update the matching row in the brief's **Decisions** table with the reason (add a row only for a new question), and keep the brief within 40 lines. If you made a decision the ADRs don't cover, suggest `/write-adr`.
- Commit: `refactor($task): <what improved>`. If nothing was refactored, fold the tick into a `chore($task): close out` commit.
- Summarize: the commits, the tests by Done-when item, the review items applied or rejected, and anything deferred. Don't push unless the user asks.
